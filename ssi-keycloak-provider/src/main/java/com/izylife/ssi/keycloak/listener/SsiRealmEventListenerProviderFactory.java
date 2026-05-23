/*
 * SSI Keycloak Provider
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.ssi.keycloak.listener;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SsiRealmEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SsiRealmEventListenerProviderFactory.class);
    public static final String PROVIDER_ID = "ssi-realm-init";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new SsiRealmEventListenerProvider(session);
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Generate DID:key for every existing realm that does not have one yet
        KeycloakModelUtils.runJobInTransaction(factory, session ->
            session.realms().getRealmsStream().forEach(realm -> {
                SsiRealmEventListenerProvider.ensureDidForRealm(realm);
            })
        );
        LOGGER.info("SSI: DID:key initialization complete for all existing realms");
    }

    @Override
    public void close() {}
}
