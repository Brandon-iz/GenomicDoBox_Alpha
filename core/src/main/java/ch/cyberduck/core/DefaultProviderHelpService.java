package ch.cyberduck.core;

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
 * Bug fixes, suggestions and comments should be sent to:
 * feedback@cyberduck.ch
 */

import ch.cyberduck.core.local.BrowserLauncher;
import ch.cyberduck.core.local.BrowserLauncherFactory;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.StringUtils;

public class DefaultProviderHelpService implements ProviderHelpService {

    private final BrowserLauncher launcher = BrowserLauncherFactory.get();

    public void help() {
        this.help(StringUtils.EMPTY);
    }

    @Override
    public void help(final Protocol provider) {
        this.help(provider.getProvider());
    }

    @Override
    public void help(final Scheme scheme) {
        this.help(scheme.name());
    }

    private void help(final String page) {
        final StringBuilder site = new StringBuilder(PreferencesFactory.get().getProperty("website.help"));
        if(StringUtils.isNotBlank(page)) {
            site.append("/").append(page);
        }
        launcher.open(site.toString());
    }
}
