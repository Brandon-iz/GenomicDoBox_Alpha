package ch.cyberduck.core.features;

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
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.io.Checksum;
import ch.cyberduck.core.transfer.TransferStatus;

import java.io.OutputStream;

public interface Write {

    /**
     * @param status Transfer status
     * @return Stream to write to for upload
     */
    OutputStream write(Path file, TransferStatus status) throws BackgroundException;

    /**
     * Determine if a file exists and we can append to it.
     *
     * @param file   File
     * @param length Transfer Status
     * @param cache  Cache
     * @return True if can append to existing file
     */
    Append append(Path file, Long length, PathCache cache) throws BackgroundException;

    /**
     * @return True if temporary upload filename can be used
     */
    boolean temporary();

    /**
     * @return True if supporting random writes with arbitrary offset and length
     */
    boolean random();

    final class Append {
        /**
         * Append to file
         */
        public boolean append;

        public boolean override;

        /**
         * Remote file size
         */
        public Long size = 0L;

        public Checksum checksum;

        /**
         * Append
         *
         * @param size Remote file size
         */
        public Append(final Long size) {
            this.append = true;
            this.override = false;
            this.size = size;
        }

        public Append(final boolean append, final boolean override) {
            this.append = append;
            this.override = override;
        }

        public Append withChecksum(final Checksum checksum) {
            this.checksum = checksum;
            return this;
        }

        public Append withSize(final Long size) {
            this.size = size;
            return this;
        }
    }

    /**
     * Existing remote file found
     */
    Append override = new Append(false, true);

    /**
     * No file found
     */
    Append notfound = new Append(false, false);
}
