package ch.cyberduck.core.b2;

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
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.shared.DefaultHomeFinderService;

import java.util.EnumSet;

public class B2HomeFinderService extends DefaultHomeFinderService {

    private final PathContainerService containerService
            = new B2PathContainerService();

    public B2HomeFinderService(final B2Session session) {
        super(session);
    }

    @Override
    public Path find() throws BackgroundException {
        final Path home = super.find();
        if(containerService.isContainer(home)) {
            return new Path(home.getAbsolute(), EnumSet.of(Path.Type.volume, Path.Type.directory));
        }
        return home;
    }

    @Override
    public Path find(final Path root, final String path) {
        final Path home = super.find(root, path);
        if(containerService.isContainer(home)) {
            return new Path(home.getAbsolute(), EnumSet.of(Path.Type.volume, Path.Type.directory));
        }
        return home;
    }
}
