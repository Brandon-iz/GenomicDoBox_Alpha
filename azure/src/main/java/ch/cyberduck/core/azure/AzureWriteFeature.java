package ch.cyberduck.core.azure;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.io/
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
 * Bug fixes, suggestions and comments should be sent to:
 * feedback@cyberduck.io
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Find;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.shared.DefaultFindFeature;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.output.ProxyOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.HashMap;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.RetryNoRetry;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobOutputStream;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.core.SR;

public class AzureWriteFeature implements Write {
    private static final Logger log = Logger.getLogger(AzureWriteFeature.class);

    private AzureSession session;

    private OperationContext context;

    private Find finder;

    private PathContainerService containerService
            = new AzurePathContainerService();

    private Preferences preferences
            = PreferencesFactory.get();

    public AzureWriteFeature(final AzureSession session, final OperationContext context) {
        this.session = session;
        this.context = context;
        this.finder = new DefaultFindFeature(session);
    }

    @Override
    public Append append(final Path file, final Long length, final PathCache cache) throws BackgroundException {
        if(finder.withCache(cache).find(file)) {
            return Write.override;
        }
        return Write.notfound;
    }

    @Override
    public boolean temporary() {
        return false;
    }

    @Override
    public boolean random() {
        return false;
    }

    @Override
    public OutputStream write(final Path file, final TransferStatus status) throws BackgroundException {
        try {
            final CloudBlockBlob blob = session.getClient().getContainerReference(containerService.getContainer(file).getName())
                    .getBlockBlobReference(containerService.getKey(file));
            if(StringUtils.isNotBlank(status.getMime())) {
                blob.getProperties().setContentType(status.getMime());
            }
            final HashMap<String, String> headers = new HashMap<>();
            // Add previous metadata when overwriting file
            headers.putAll(status.getMetadata());
            blob.setMetadata(headers);
            // Remove additional headers not allowed in metadata and move to properties
            if(headers.containsKey(HttpHeaders.CACHE_CONTROL)) {
                blob.getProperties().setCacheControl(headers.get(HttpHeaders.CACHE_CONTROL));
                headers.remove(HttpHeaders.CACHE_CONTROL);
            }
            if(headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                blob.getProperties().setCacheControl(headers.get(HttpHeaders.CONTENT_TYPE));
                headers.remove(HttpHeaders.CONTENT_TYPE);
            }
            final BlobRequestOptions options = new BlobRequestOptions();
            options.setConcurrentRequestCount(1);
            options.setRetryPolicyFactory(new RetryNoRetry());
            options.setStoreBlobContentMD5(preferences.getBoolean("azure.upload.md5"));
            final BlobOutputStream out = blob.openOutputStream(AccessCondition.generateEmptyCondition(), options, context);
            return new ProxyOutputStream(out) {
                @Override
                protected void handleIOException(final IOException e) throws IOException {
                    if(StringUtils.equals(SR.STREAM_CLOSED, e.getMessage())) {
                        log.warn(String.format("Ignore failure %s", e));
                        return;
                    }
                    throw e;
                }
            };
        }
        catch(StorageException e) {
            throw new AzureExceptionMappingService().map("Upload {0} failed", e, file);
        }
        catch(URISyntaxException e) {
            throw new NotfoundException(e.getMessage(), e);
        }
    }
}
