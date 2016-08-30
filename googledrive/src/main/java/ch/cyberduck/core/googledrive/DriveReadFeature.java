package ch.cyberduck.core.googledrive;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
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
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.http.HttpRange;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

import com.google.api.services.drive.Drive;

public class DriveReadFeature implements Read {
    private static final Logger log = Logger.getLogger(DriveReadFeature.class);

    private DriveSession session;

    public DriveReadFeature(DriveSession session) {
        this.session = session;
    }

    @Override
    public boolean offset(Path file) {
        return true;
    }

    @Override
    public InputStream read(final Path file, final TransferStatus status) throws BackgroundException {
        try {
            final Drive.Files.Get request = session.getClient().files().get(new DriveFileidProvider(session).getFileid(file));
            if(status.isAppend()) {
                final HttpRange range = HttpRange.withStatus(status);
                final String header;
                if(-1 == range.getEnd()) {
                    header = String.format("bytes=%d-", range.getStart());
                }
                else {
                    header = String.format("bytes=%d-%d", range.getStart(), range.getEnd());
                }
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Add range header %s for file %s", header, file));
                }
                request.getRequestHeaders().setRange(header);
                // Disable compression
                request.getRequestHeaders().setAcceptEncoding("identity");
            }
            return request.executeMediaAsInputStream();
        }
        catch(IOException e) {
            throw new DriveExceptionMappingService().map("Download failed", e, file);
        }
    }
}
