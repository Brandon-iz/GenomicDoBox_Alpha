package ch.cyberduck.core.transfer.download;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.DescriptiveUrl;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.HostUrlProvider;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.ProgressListener;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.UrlProvider;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.ChecksumException;
import ch.cyberduck.core.exception.LocalAccessDeniedException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Attributes;
import ch.cyberduck.core.features.Download;
import ch.cyberduck.core.io.Checksum;
import ch.cyberduck.core.io.ChecksumCompute;
import ch.cyberduck.core.io.ChecksumComputeFactory;
import ch.cyberduck.core.local.ApplicationLauncher;
import ch.cyberduck.core.local.ApplicationLauncherFactory;
import ch.cyberduck.core.local.IconService;
import ch.cyberduck.core.local.IconServiceFactory;
import ch.cyberduck.core.local.QuarantineService;
import ch.cyberduck.core.local.QuarantineServiceFactory;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.shared.DefaultAttributesFeature;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferPathFilter;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.symlink.SymlinkResolver;

import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractDownloadFilter implements TransferPathFilter {
    private static final Logger log = Logger.getLogger(AbstractDownloadFilter.class);

    private final SymlinkResolver<Path> symlinkResolver;

    private final QuarantineService quarantine
            = QuarantineServiceFactory.get();

    private final ApplicationLauncher launcher
            = ApplicationLauncherFactory.get();

    private final Preferences preferences
            = PreferencesFactory.get();

    private final IconService icon
            = IconServiceFactory.get();

    private final Session<?> session;

    private Attributes attribute;

    private DownloadFilterOptions options;

    protected AbstractDownloadFilter(final SymlinkResolver<Path> symlinkResolver, final Session<?> session,
                                     final DownloadFilterOptions options) {
        this.symlinkResolver = symlinkResolver;
        this.session = session;
        this.options = options;
        this.attribute = new DefaultAttributesFeature(session);
    }

    @Override
    public AbstractDownloadFilter withCache(final PathCache cache) {
        attribute.withCache(cache);
        return this;
    }

    public AbstractDownloadFilter withAttributes(final Attributes attribute) {
        this.attribute = attribute;
        return this;
    }

    public AbstractDownloadFilter withOptions(final DownloadFilterOptions options) {
        this.options = options;
        return this;
    }

    @Override
    public boolean accept(final Path file, final Local local, final TransferStatus parent) throws BackgroundException {
        final Local volume = local.getVolume();
        if(!volume.exists()) {
            throw new NotfoundException(String.format("Volume %s not mounted", volume.getAbsolute()));
        }
        return true;
    }

    @Override
    public TransferStatus prepare(final Path file, final Local local, final TransferStatus parent) throws BackgroundException {
        final TransferStatus status = new TransferStatus();
        if(parent.isExists()) {
            if(local.exists()) {
                if(file.getType().contains(Path.Type.file)) {
                    if(local.isDirectory()) {
                        throw new LocalAccessDeniedException(String.format("Cannot replace folder %s with file %s", local.getAbbreviatedPath(), file.getName()));
                    }
                }
                if(file.getType().contains(Path.Type.directory)) {
                    if(local.isFile()) {
                        throw new LocalAccessDeniedException(String.format("Cannot replace file %s with folder %s", local.getAbbreviatedPath(), file.getName()));
                    }
                }
                status.setExists(true);
            }
        }
        final PathAttributes attributes;
        if(file.isSymbolicLink()) {
            // A server will resolve the symbolic link when the file is requested.
            final Path target = file.getSymlinkTarget();
            // Read remote attributes of symlink target
            attributes = attribute.find(target);
            if(!symlinkResolver.resolve(file)) {
                if(file.isFile()) {
                    // Content length
                    status.setLength(attributes.getSize());
                }
            }
            // No file size increase for symbolic link to be created locally
        }
        else {
            // Read remote attributes
            attributes = attribute.find(file);
            if(file.isFile()) {
                // Content length
                status.setLength(attributes.getSize());
            }
        }
        status.setRemote(attributes);
        if(options.timestamp) {
            status.setTimestamp(attributes.getModificationDate());
        }
        if(options.permissions) {
            Permission permission = Permission.EMPTY;
            if(preferences.getBoolean("queue.download.permissions.default")) {
                if(file.isFile()) {
                    permission = new Permission(
                            preferences.getInteger("queue.download.permissions.file.default"));
                }
                if(file.isDirectory()) {
                    permission = new Permission(
                            preferences.getInteger("queue.download.permissions.folder.default"));
                }
            }
            else {
                permission = attributes.getPermission();
            }
            status.setPermission(permission);
        }
        status.setAcl(attributes.getAcl());
        if(options.segments) {
            if(file.isFile()) {
                if(session.getTransferType() == Host.TransferType.concurrent) {
                    // Make segments
                    if(status.getLength() >= preferences.getLong("queue.download.segments.threshold")
                            && status.getLength() > preferences.getLong("queue.download.segments.size")) {
                        final Download read = session.getFeature(Download.class);
                        if(read.offset(file)) {
                            if(log.isInfoEnabled()) {
                                log.info(String.format("Split download %s into segments", local));
                            }
                            long remaining = status.getLength();
                            long offset = 0;
                            // Part size from default setting of size divided by maximum number of connections
                            long partsize = Math.max(
                                    preferences.getLong("queue.download.segments.size"),
                                    status.getLength() / preferences.getInteger("queue.maxtransfers"));
                            // Sorted list
                            final List<TransferStatus> segments = new ArrayList<TransferStatus>();
                            for(int segmentNumber = 1; remaining > 0; segmentNumber++) {
                                final Local segmentFile = LocalFactory.get(
                                        LocalFactory.get(local.getParent(), String.format("%s.cyberducksegment", local.getName())),
                                        String.format("%s-%d.cyberducksegment", local.getName(), segmentNumber));
                                boolean skip = false;
                                // Last part can be less than 5 MB. Adjust part size.
                                Long length = Math.min(partsize, remaining);
                                final TransferStatus segmentStatus = new TransferStatus()
                                        .segment(true)
                                        .append(true)
                                        .skip(offset)
                                        .length(length)
                                        .rename(segmentFile);
                                if(log.isDebugEnabled()) {
                                    log.debug(String.format("Adding status %s for segment %s", segmentStatus, segmentFile));
                                }
                                segments.add(segmentStatus);
                                remaining -= length;
                                offset += length;
                            }
                            status.withSegments(segments);
                        }
                    }
                }
            }
        }
        if(options.checksum) {
            status.setChecksum(attributes.getChecksum());
        }
        return status;
    }

    @Override
    public void apply(final Path file, final Local local, final TransferStatus status,
                      final ProgressListener listener) throws BackgroundException {
        //
    }

    /**
     * Update timestamp and permission
     */
    @Override
    public void complete(final Path file, final Local local,
                         final TransferOptions options, final TransferStatus status,
                         final ProgressListener listener) throws BackgroundException {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Complete %s with status %s", file.getAbsolute(), status));
        }
        if(status.isSegment()) {
            if(log.isDebugEnabled()) {
                log.debug(String.format("Skip completion for single segment %s", status));
            }
            return;
        }
        if(status.isComplete()) {
            if(status.isSegmented()) {
                // Obtain ordered list of segments to reassemble
                final List<TransferStatus> segments = status.getSegments();
                if(log.isInfoEnabled()) {
                    log.info(String.format("Compile %d segments to file %s", segments.size(), local));
                }
                if(local.exists()) {
                    local.delete();
                }
                for(Iterator<TransferStatus> iterator = segments.iterator(); iterator.hasNext(); ) {
                    final TransferStatus segmentStatus = iterator.next();
                    // Segment
                    final Local segmentFile = segmentStatus.getRename().local;
                    if(log.isInfoEnabled()) {
                        log.info(String.format("Append segment %s to %s", segmentFile, local));
                    }
                    segmentFile.copy(local, new Local.CopyOptions().append(true));
                    if(log.isInfoEnabled()) {
                        log.info(String.format("Delete segment %s", segmentFile));
                    }
                    segmentFile.delete();
                    if(!iterator.hasNext()) {
                        final Local folder = segmentFile.getParent();
                        if(log.isInfoEnabled()) {
                            log.info(String.format("Remove segment folder %s", folder));
                        }
                        folder.delete();
                    }
                }
            }
            if(log.isDebugEnabled()) {
                log.debug(String.format("Run completion for file %s with status %s", local, status));
            }
            if(file.isFile()) {
                // Remove custom icon if complete. The Finder will display the default icon for this file type
                if(this.options.icon) {
                    icon.set(local, status);
                    icon.remove(local);
                }
                final DescriptiveUrl provider = session.getFeature(UrlProvider.class).toUrl(file).find(DescriptiveUrl.Type.provider);
                if(!DescriptiveUrl.EMPTY.equals(provider)) {
                    try {
                        if(options.quarantine) {
                            // Set quarantine attributes
                            quarantine.setQuarantine(local, new HostUrlProvider(false).get(session.getHost()),
                                    provider.getUrl());
                        }
                        if(this.options.wherefrom) {
                            // Set quarantine attributes
                            quarantine.setWhereFrom(local, provider.getUrl());
                        }
                    }
                    catch(LocalAccessDeniedException e) {
                        log.warn(String.format("Failure to quarantine file %s. %s", file, e.getMessage()));
                    }
                }
                if(options.open) {
                    launcher.open(local);
                }
            }
            if(!Permission.EMPTY.equals(status.getPermission())) {
                if(file.isDirectory()) {
                    // Make sure we can read & write files to directory created.
                    status.getPermission().setUser(status.getPermission().getUser().or(Permission.Action.read).or(Permission.Action.write).or(Permission.Action.execute));
                }
                if(file.isFile()) {
                    // Make sure the owner can always read and write.
                    status.getPermission().setUser(status.getPermission().getUser().or(Permission.Action.read).or(Permission.Action.write));
                }
                if(log.isInfoEnabled()) {
                    log.info(String.format("Updating permissions of %s to %s", local, status.getPermission()));
                }
                try {
                    local.attributes().setPermission(status.getPermission());
                }
                catch(AccessDeniedException e) {
                    // Ignore
                    log.warn(e.getMessage());
                }
            }
            if(status.getTimestamp() != null) {
                if(log.isInfoEnabled()) {
                    log.info(String.format("Updating timestamp of %s to %d", local, status.getTimestamp()));
                }
                try {
                    local.attributes().setModificationDate(status.getTimestamp());
                }
                catch(AccessDeniedException e) {
                    // Ignore
                    log.warn(e.getMessage());
                }
            }
            if(file.isFile()) {
                if(this.options.checksum) {
                    final Checksum checksum = status.getChecksum();
                    if(null != checksum) {
                        final ChecksumCompute compute = ChecksumComputeFactory.get(checksum.algorithm);
                        final Checksum download = compute.compute(local.getInputStream());
                        if(!checksum.equals(download)) {
                            throw new ChecksumException(
                                    MessageFormat.format(LocaleFactory.localizedString("Download {0} failed", "Error"), file.getName()),
                                    MessageFormat.format("Mismatch between {0} hash {1} of downloaded data and ETag {2} returned by the server",
                                            download.algorithm.toString(), download.hash, checksum.hash));
                        }
                    }
                }
            }
            launcher.bounce(local);
        }
    }
}
