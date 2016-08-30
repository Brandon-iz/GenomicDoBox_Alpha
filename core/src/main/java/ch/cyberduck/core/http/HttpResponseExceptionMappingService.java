package ch.cyberduck.core.http;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.io/
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
 * feedback@cyberduck.io
 */

import ch.cyberduck.core.AbstractExceptionMappingService;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.ConnectionRefusedException;
import ch.cyberduck.core.exception.ConnectionTimeoutException;
import ch.cyberduck.core.exception.InteroperabilityException;
import ch.cyberduck.core.exception.LoginFailureException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.exception.QuotaException;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

public class HttpResponseExceptionMappingService extends AbstractExceptionMappingService<HttpResponseException> {

    @Override
    public BackgroundException map(final HttpResponseException failure) {
        final StringBuilder buffer = new StringBuilder();
        this.append(buffer, failure.getMessage());
        final int statusCode = failure.getStatusCode();
        switch(statusCode) {
            case HttpStatus.SC_UNAUTHORIZED:
                return new LoginFailureException(buffer.toString(), failure);
            case HttpStatus.SC_FORBIDDEN:
                return new AccessDeniedException(buffer.toString(), failure);
            case HttpStatus.SC_NOT_FOUND:
                return new NotfoundException(buffer.toString(), failure);
            case HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE:
                return new QuotaException(buffer.toString(), failure);
            case HttpStatus.SC_INSUFFICIENT_STORAGE:
                return new QuotaException(buffer.toString(), failure);
            case HttpStatus.SC_PAYMENT_REQUIRED:
                return new QuotaException(buffer.toString(), failure);
            case HttpStatus.SC_BAD_REQUEST:
                return new InteroperabilityException(buffer.toString(), failure);
            case HttpStatus.SC_METHOD_NOT_ALLOWED:
                return new InteroperabilityException(buffer.toString(), failure);
            case HttpStatus.SC_NOT_IMPLEMENTED:
                return new InteroperabilityException(buffer.toString(), failure);
            case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                return new InteroperabilityException(buffer.toString(), failure);
            case HttpStatus.SC_SERVICE_UNAVAILABLE:
                return new ConnectionRefusedException(buffer.toString(), failure);
            case HttpStatus.SC_REQUEST_TIMEOUT:
                return new ConnectionTimeoutException(buffer.toString(), failure);
        }
        return this.wrap(failure, buffer);
    }
}
