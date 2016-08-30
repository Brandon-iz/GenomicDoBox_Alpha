package ch.cyberduck.core.updater;

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

import ch.cyberduck.core.preferences.PreferencesFactory;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractPeriodicUpdateChecker implements PeriodicUpdateChecker {
    private static Logger log = Logger.getLogger(AbstractPeriodicUpdateChecker.class.getName());

    private final Duration delay;

    private Timer timer = new Timer("updater", true);

    /**
     * Defaults to 24 hours
     */
    public AbstractPeriodicUpdateChecker() {
        this(Duration.ofSeconds(PreferencesFactory.get().getLong("update.check.interval")));
    }

    public AbstractPeriodicUpdateChecker(final Duration delay) {
        this.delay = delay;
    }

    @Override
    public void unregister() {
        timer.cancel();
    }

    @Override
    public Duration register() {
        log.info(String.format("Register update checker hook after %s", delay));
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(log.isLoggable(Level.FINE)) {
                    log.fine(String.format("Check for new updates after %s", delay));
                }
                PreferencesFactory.get().setProperty("update.check.timestamp", System.currentTimeMillis());
                check(true);
            }
        }, delay.toMillis());
        return delay;
    }

    @Override
    public boolean hasUpdatePrivileges() {
        return true;
    }
}
