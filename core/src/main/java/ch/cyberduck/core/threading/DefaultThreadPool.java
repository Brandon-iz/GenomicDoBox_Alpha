package ch.cyberduck.core.threading;

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

import ch.cyberduck.core.preferences.PreferencesFactory;

import org.apache.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DefaultThreadPool<T> extends ExecutorServiceThreadPool<T> {
    private static final Logger log = Logger.getLogger(DefaultThreadPool.class);

    /**
     * With FIFO (first-in-first-out) ordered wait queue.
     */
    public DefaultThreadPool() {
        super(Executors.newSingleThreadExecutor(new NamedThreadFactory("background")));
    }

    public DefaultThreadPool(final Thread.UncaughtExceptionHandler handler) {
        super(new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                PreferencesFactory.get().getLong("threading.pool.keepalive.seconds"), TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new NamedThreadFactory("background", handler)));
    }

    /**
     * With FIFO (first-in-first-out) ordered wait queue.
     *
     * @param size Number of concurrent threads
     */
    public DefaultThreadPool(final int size) {
        this(size, "background");
    }

    public DefaultThreadPool(final String prefix) {
        super(new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                PreferencesFactory.get().getLong("threading.pool.keepalive.seconds"), TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new NamedThreadFactory(prefix)));
    }

    public DefaultThreadPool(final int size, final String prefix) {
        super(1 == size ?
                Executors.newSingleThreadExecutor(new NamedThreadFactory("background")) :
                Executors.newFixedThreadPool(size, new NamedThreadFactory(prefix)));
    }

    public DefaultThreadPool(final int size, final Thread.UncaughtExceptionHandler handler) {
        super(1 == size ?
                Executors.newSingleThreadExecutor(new NamedThreadFactory("background", handler)) :
                Executors.newFixedThreadPool(size, new NamedThreadFactory("background", handler)));
    }
}