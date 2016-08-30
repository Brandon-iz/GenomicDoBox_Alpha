package ch.cyberduck.core.threading;

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

import ch.cyberduck.core.Factory;
import ch.cyberduck.core.FactoryException;
import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ThreadPoolFactory extends Factory<ThreadPool> {
    private static final Logger log = Logger.getLogger(ThreadPoolFactory.class);

    public ThreadPoolFactory() {
        super("factory.threadpool.class");
    }

    protected ThreadPool create(final Thread.UncaughtExceptionHandler handler) {
        final String clazz = PreferencesFactory.get().getProperty("factory.threadpool.class");
        if(null == clazz) {
            throw new FactoryException(String.format("No implementation given for factory %s", this.getClass().getSimpleName()));
        }
        try {
            final Class<ThreadPool> name = (Class<ThreadPool>) Class.forName(clazz);
            final Constructor<ThreadPool> constructor = ConstructorUtils.getMatchingAccessibleConstructor(name, handler.getClass());
            if(null == constructor) {
                log.warn(String.format("No matching constructor for parameter %s", handler.getClass()));
                // Call default constructor for disabled implementations
                return name.newInstance();
            }
            return constructor.newInstance(handler);
        }
        catch(InstantiationException | InvocationTargetException | ClassNotFoundException | IllegalAccessException e) {
            throw new FactoryException(e.getMessage(), e);
        }
    }

    public static ThreadPool get() {
        return new ThreadPoolFactory().create();
    }

    public static ThreadPool get(final Thread.UncaughtExceptionHandler handler) {
        return new ThreadPoolFactory().create(handler);
    }
}
