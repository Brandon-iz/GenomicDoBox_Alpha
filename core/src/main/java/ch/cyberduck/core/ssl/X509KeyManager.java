package ch.cyberduck.core.ssl;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
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
 * Bug fixes, suggestions and comments should be sent to:
 * feedback@cyberduck.ch
 */

import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

public interface X509KeyManager extends javax.net.ssl.X509KeyManager {

    /**
     * Load the key store.
     */
    X509KeyManager init() throws IOException;

    /**
     * Find matching certificate for alias in key store
     *
     * @param issuers Acceptable CA issuer subject names or null if it does not matter which issuers are used
     */
    X509Certificate getCertificate(String alias, String[] keyTypes, Principal[] issuers);
}
