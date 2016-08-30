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
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class CertificateStoreFactory extends Factory<CertificateStore> {
    private static final Logger log = Logger.getLogger(CertificateStoreFactory.class);

    private static final Preferences preferences
            = PreferencesFactory.get();

    protected CertificateStoreFactory() {
        super("factory.certificatestore.class");
    }

    public CertificateStore create(final Controller c) {
        final String clazz = preferences.getProperty("factory.certificatestore.class");
        if(null == clazz) {
            throw new FactoryException(String.format("No implementation given for factory %s", this.getClass().getSimpleName()));
        }
        try {
            final Class<CertificateStore> name = (Class<CertificateStore>) Class.forName(clazz);
            final Constructor<CertificateStore> constructor = ConstructorUtils.getMatchingAccessibleConstructor(name, c.getClass());
            if(null == constructor) {
                log.warn(String.format("No matching constructor for parameter %s", c.getClass()));
                // Call default constructor for disabled implementations
                return name.newInstance();
            }
            return constructor.newInstance(c);
        }
        catch(InstantiationException | InvocationTargetException | ClassNotFoundException | IllegalAccessException e) {
            throw new FactoryException(e.getMessage(), e);
        }
    }

    public static CertificateStore get() {
        return new CertificateStoreFactory().create();
    }

    public static CertificateStore get(final Controller c) {
        return new CertificateStoreFactory().create(c);
    }
}
