package dev.ssi.keycloak.oidc4vc.ui;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ThemeResourceProvider;
import org.keycloak.theme.ThemeResourceProviderFactory;

/**
 * Registers the static React bundle so Keycloak can serve it as part of the admin console.
 */
public final class Oidc4vcAdminThemeResourceProviderFactory implements ThemeResourceProviderFactory {
    public static final String PROVIDER_ID = "oidc4vc-admin-ui";
    private static final Logger LOG = Logger.getLogger(Oidc4vcAdminThemeResourceProviderFactory.class);

    @Override
    public ThemeResourceProvider create(final KeycloakSession session) {
        return new Oidc4vcAdminThemeResourceProvider(session);
    }

    @Override
    public void init(final Config.Scope config) {
        LOG.info("Initializing OIDC4VC admin UI theme resource provider");
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        // nothing to do
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
