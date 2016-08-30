package ch.cyberduck.core.googlestorage;

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

import ch.cyberduck.core.Cache;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.HostKeyCallback;
import ch.cyberduck.core.HostPasswordStore;
import ch.cyberduck.core.LoginCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.TranscriptListener;
import ch.cyberduck.core.UrlProvider;
import ch.cyberduck.core.cdn.DistributionConfiguration;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.LoginFailureException;
import ch.cyberduck.core.features.AclPermission;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Directory;
import ch.cyberduck.core.features.Encryption;
import ch.cyberduck.core.features.Lifecycle;
import ch.cyberduck.core.features.Logging;
import ch.cyberduck.core.features.Redundancy;
import ch.cyberduck.core.features.Upload;
import ch.cyberduck.core.features.Versioning;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.http.HttpConnectionPoolBuilder;
import ch.cyberduck.core.identity.DefaultCredentialsIdentityConfiguration;
import ch.cyberduck.core.identity.IdentityConfiguration;
import ch.cyberduck.core.oauth.OAuth2AuthorizationService;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.proxy.ProxyFinder;
import ch.cyberduck.core.s3.RequestEntityRestStorageService;
import ch.cyberduck.core.s3.S3DefaultDeleteFeature;
import ch.cyberduck.core.s3.S3DisabledMultipartService;
import ch.cyberduck.core.s3.S3Session;
import ch.cyberduck.core.s3.S3SingleUploadService;
import ch.cyberduck.core.s3.S3WriteFeature;
import ch.cyberduck.core.ssl.X509KeyManager;
import ch.cyberduck.core.ssl.X509TrustManager;
import ch.cyberduck.core.threading.CancelCallback;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.log4j.Logger;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.impl.rest.AccessControlListHandler;
import org.jets3t.service.impl.rest.GSAccessControlListHandler;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.WebsiteConfig;
import org.jets3t.service.security.ProviderCredentials;
import org.jets3t.service.utils.oauth.OAuthConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;

import com.google.api.client.auth.oauth2.Credential;

public class GoogleStorageSession extends S3Session {
    private static final Logger log = Logger.getLogger(GoogleStorageSession.class);

    private Preferences preferences
            = PreferencesFactory.get();

    private final OAuth2AuthorizationService authorizationService = new OAuth2AuthorizationService(this,
            OAuthConstants.GSOAuth2_10.Endpoints.Token,
            OAuthConstants.GSOAuth2_10.Endpoints.Authorization,
            preferences.getProperty("google.storage.oauth.clientid"),
            preferences.getProperty("google.storage.oauth.secret"),
            Collections.singletonList(OAuthConstants.GSOAuth2_10.Scopes.FullControl.toString())
    ).withLegacyPrefix("Google");

    public GoogleStorageSession(final Host h) {
        super(h);
    }

    public GoogleStorageSession(final Host host, final X509TrustManager trust, final X509KeyManager key) {
        super(host, trust, key);
    }

    public GoogleStorageSession(final Host host, final X509TrustManager trust, final X509KeyManager key, final ProxyFinder proxy) {
        super(host, trust, key, proxy);
    }

    @Override
    protected Jets3tProperties configure() {
        final Jets3tProperties configuration = super.configure();
        configuration.setProperty("s3service.enable-storage-classes", String.valueOf(false));
        configuration.setProperty("s3service.disable-dns-buckets", String.valueOf(true));
        return configuration;
    }

    @Override
    public RequestEntityRestStorageService connect(final HostKeyCallback key) throws BackgroundException {
        return new OAuth2RequestEntityRestStorageService(this, this.configure(), builder, this);
    }

    @Override
    protected boolean authorize(final HttpUriRequest request, final ProviderCredentials credentials) throws ServiceException {
        if(credentials instanceof OAuth2ProviderCredentials) {
            request.setHeader("x-goog-api-version", "2");
            final Credential tokens = ((OAuth2ProviderCredentials) credentials).getTokens();
            if(log.isDebugEnabled()) {
                log.debug(String.format("Authorizing service request with OAuth2 access token: %s", tokens.getAccessToken()));
            }
            request.setHeader("Authorization", String.format("OAuth %s", tokens.getAccessToken()));
            return true;
        }
        return false;
    }

    @Override
    public void login(final HostPasswordStore keychain, final LoginCallback prompt,
                      final CancelCallback cancel, final Cache<Path> cache) throws BackgroundException {

        final Credential tokens = authorizationService.authorize(host, keychain, prompt);

        client.setProviderCredentials(new OAuth2ProviderCredentials(tokens, preferences.getProperty("google.storage.oauth.clientid"),
                preferences.getProperty("google.storage.oauth.secret")));

        if(host.getCredentials().isPassed()) {
            log.warn(String.format("Skip verifying credentials with previous successful authentication event for %s", this));
            return;
        }
        // List all buckets and cache
        try {
            final Path root = new Path(String.valueOf(Path.DELIMITER), EnumSet.of(Path.Type.directory, Path.Type.volume));
            cache.put(root, this.list(root, new DisabledListProgressListener()));
        }
        catch(BackgroundException e) {
            throw new LoginFailureException(e.getDetail(false), e);
        }
    }

    @Override
    protected XmlResponsesSaxParser getXmlResponseSaxParser() throws ServiceException {
        return new XmlResponsesSaxParser(this.configure(), false) {
            @Override
            public AccessControlListHandler parseAccessControlListResponse(InputStream inputStream) throws ServiceException {
                return this.parseAccessControlListResponse(inputStream, new GSAccessControlListHandler());
            }

            @Override
            public BucketLoggingStatusHandler parseLoggingStatusResponse(InputStream inputStream) throws ServiceException {
                return super.parseLoggingStatusResponse(inputStream, new GSBucketLoggingStatusHandler());
            }

            @Override
            public WebsiteConfig parseWebsiteConfigurationResponse(InputStream inputStream) throws ServiceException {
                return super.parseWebsiteConfigurationResponse(inputStream, new GSWebsiteConfigurationHandler());
            }
        };
    }

    /**
     * @return the identifier for the signature algorithm.
     */
    @Override
    protected String getSignatureIdentifier() {
        return "GOOG1";
    }

    /**
     * @return header prefix for general Google Storage headers: x-goog-.
     */
    @Override
    protected String getRestHeaderPrefix() {
        return "x-goog-";
    }

    /**
     * @return header prefix for Google Storage metadata headers: x-goog-meta-.
     */
    @Override
    protected String getRestMetadataPrefix() {
        return "x-goog-meta-";
    }

    private static final class OAuth2ProviderCredentials extends ProviderCredentials {
        private final Credential tokens;

        public OAuth2ProviderCredentials(final Credential tokens, final String clientId, final String clientSecret) {
            super(clientId, clientSecret);
            this.tokens = tokens;
        }

        @Override
        protected String getTypeName() {
            return StringUtils.EMPTY;
        }

        @Override
        protected String getVersionPrefix() {
            return StringUtils.EMPTY;
        }

        public Credential getTokens() {
            return tokens;
        }
    }

    private final class OAuth2RequestEntityRestStorageService extends RequestEntityRestStorageService {
        public OAuth2RequestEntityRestStorageService(final GoogleStorageSession session, final Jets3tProperties configuration, final HttpConnectionPoolBuilder pool,
                                                     final TranscriptListener listener) {
            super(session, configuration, pool, listener);
        }

        @Override
        protected boolean isRecoverable403(final HttpUriRequest httpRequest, final Exception exception) {
            final Credential tokens = ((OAuth2ProviderCredentials) getProviderCredentials()).getTokens();
            try {
                if(tokens.refreshToken()) {
                    return true;
                }
            }
            catch(IOException e) {
                log.warn(String.format("Failure refreshing OAuth2 tokens. %s", e.getMessage()));
                return false;
            }
            return super.isRecoverable403(httpRequest, exception);
        }

        @Override
        protected StorageBucket createBucketImpl(String bucketName, String location,
                                                 AccessControlList acl) throws ServiceException {
            return super.createBucketImpl(bucketName, location, acl,
                    Collections.singletonMap("x-goog-project-id", host.getCredentials().getUsername()));
        }

        @Override
        protected StorageBucket[] listAllBucketsImpl() throws ServiceException {
            return super.listAllBucketsImpl(
                    Collections.singletonMap("x-goog-project-id", host.getCredentials().getUsername()));
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getFeature(final Class<T> type) {
        if(type == Upload.class) {
            return (T) new S3SingleUploadService(this);
        }
        if(type == Write.class) {
            return (T) new S3WriteFeature(this, new S3DisabledMultipartService());
        }
        if(type == Delete.class) {
            return (T) new S3DefaultDeleteFeature(this);
        }
        if(type == Directory.class) {
            return (T) new GoogleStorageDirectoryFeature(this);
        }
        if(type == AclPermission.class) {
            return (T) new GoogleStorageAccessControlListFeature(this);
        }
        if(type == DistributionConfiguration.class) {
            return (T) new GoogleStorageWebsiteDistributionConfiguration(this);
        }
        if(type == IdentityConfiguration.class) {
            return (T) new DefaultCredentialsIdentityConfiguration(host);
        }
        if(type == Logging.class) {
            return (T) new GoogleStorageLoggingFeature(this);
        }
        if(type == Lifecycle.class) {
            return null;
        }
        if(type == Versioning.class) {
            return null;
        }
        if(type == Encryption.class) {
            return null;
        }
        if(type == Redundancy.class) {
            return null;
        }
        if(type == UrlProvider.class) {
            return (T) new GoogleStorageUrlProvider(this);
        }
        return super.getFeature(type);
    }
}
