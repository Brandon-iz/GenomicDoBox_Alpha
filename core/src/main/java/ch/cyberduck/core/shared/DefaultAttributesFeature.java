package ch.cyberduck.core.shared;

/*
 * Copyright (c) 2013 David Kocher. All rights reserved.
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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.InteroperabilityException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Attributes;
import ch.cyberduck.core.features.IdProvider;

import org.apache.log4j.Logger;

public class DefaultAttributesFeature implements Attributes {
    private static final Logger log = Logger.getLogger(DefaultAttributesFeature.class);

    private Session<?> session;

    private PathCache cache
            = PathCache.empty();

    public DefaultAttributesFeature(final Session session) {
        this.session = session;
    }

    @Override
    public PathAttributes find(final Path file) throws BackgroundException {
        if(file.isRoot()) {
            return PathAttributes.EMPTY;
        }
        final AttributedList<Path> list;
        if(!cache.containsKey(file.getParent())) {
            try {
                list = session.list(file.getParent(), new DisabledListProgressListener());
                cache.put(file.getParent(), list);
            }
            catch(InteroperabilityException | AccessDeniedException | NotfoundException f) {
                log.warn(String.format("Failure listing directory %s. %s", file.getParent(), f.getMessage()));
                // Try native implementation
                final Attributes feature = session.getFeature(Attributes.class);
                if(feature instanceof DefaultAttributesFeature) {
                    throw f;
                }
                return feature.withCache(cache).find(file);
            }
        }
        else {
            list = cache.get(file.getParent());
        }
        if(list.contains(file)) {
            return list.get(file).attributes();
        }
        else {
            if(null == file.attributes().getVersionId()) {
                // Try native implementation
                final Attributes feature = session.getFeature(Attributes.class);
                if(feature instanceof DefaultAttributesFeature) {
                    throw new NotfoundException(file.getAbsolute());
                }
                final IdProvider id = session.getFeature(IdProvider.class);
                final String version = id.getFileid(file);
                if(version == null) {
                    throw new NotfoundException(file.getAbsolute());
                }
                final PathAttributes attributes = new PathAttributes();
                attributes.setVersionId(version);
                return feature.find(new Path(file.getAbsolute(), file.getType(), attributes));
            }
            throw new NotfoundException(file.getAbsolute());
        }
    }

    @Override
    public DefaultAttributesFeature withCache(final PathCache cache) {
        this.cache = cache;
        return this;
    }
}
