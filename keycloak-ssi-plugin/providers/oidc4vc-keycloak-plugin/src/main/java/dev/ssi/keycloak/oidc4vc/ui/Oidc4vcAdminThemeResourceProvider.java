package dev.ssi.keycloak.oidc4vc.ui;

import java.io.IOException;
import java.io.InputStream;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.theme.ThemeResourceProvider;

/**
 * Simple resource provider that exposes the static admin UI bundle packaged in the plugin JAR.
 */
public final class Oidc4vcAdminThemeResourceProvider implements ThemeResourceProvider {
    private static final Logger LOG = Logger.getLogger(Oidc4vcAdminThemeResourceProvider.class);
    private static final String RESOURCE_ROOT = "theme-resources/";

    private final ClassLoader classLoader;

    public Oidc4vcAdminThemeResourceProvider(final KeycloakSession session) {
        this.classLoader = getClass().getClassLoader();
    }

    @Override
    public InputStream getResourceAsStream(final String path) throws IOException {
        final String resolvedPath = RESOURCE_ROOT + path;
        final InputStream stream = classLoader.getResourceAsStream(resolvedPath);
        if (stream == null && LOG.isDebugEnabled()) {
            LOG.debugf("OIDC4VC admin UI resource not found: %s", resolvedPath);
        }
        return stream;
    }

    @Override
    public java.net.URL getTemplate(final String name) throws IOException {
        // This provider only exposes static JavaScript and does not contribute Freemarker templates.
        return null;
    }

    @Override
    public void close() {
        // nothing to close
    }
}
