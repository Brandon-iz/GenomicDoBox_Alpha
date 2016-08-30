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

import org.rococoa.ID;
import org.rococoa.ObjCClass;
import org.rococoa.Rococoa;
import org.rococoa.Selector;

public abstract class NSInvocationOperation extends NSOperation {
    public static final _Class CLASS = Rococoa.createClass(NSInvocationOperation.class.getSimpleName(), _Class.class);

    public interface _Class extends ObjCClass {
        public NSInvocationOperation alloc();
    }

    public abstract NSInvocationOperation initWithTarget_selector_object(ID target, Selector sel, ID arg);

    public abstract ID result();
}