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

import com.izylife.ssi.keycloak.resource.DidResource;
import com.izylife.ssi.keycloak.util.DidKeyUtil;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Listens for realm-creation admin events and auto-generates a DID:key
 * for the new realm if one does not already exist.
 */
public class SsiRealmEventListenerProvider implements EventListenerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SsiRealmEventListenerProvider.class);

    private final KeycloakSession session;

    public SsiRealmEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        // User-level events are not relevant here
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent.getResourceType() != ResourceType.REALM
                || adminEvent.getOperationType() != OperationType.CREATE) {
            return;
        }
        // The resource path for a realm creation event is the realm name
        String realmName = adminEvent.getResourcePath();
        if (realmName == null || realmName.isBlank()) {
            return;
        }
        RealmModel realm = session.realms().getRealmByName(realmName);
        if (realm == null) {
            return;
        }
        ensureDidForRealm(realm);
    }

    @Override
    public void close() {}

    static void ensureDidForRealm(RealmModel realm) {
        if (realm.getAttribute(DidResource.ATTR_DID) != null) {
            return;
        }
        try {
            KeyPair kp = DidKeyUtil.generateKeyPair();
            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
            String did = DidKeyUtil.deriveDid(pub);
            realm.setAttribute(DidResource.ATTR_DID, did);
            realm.setAttribute(DidResource.ATTR_PUBLIC_JWK, DidKeyUtil.toPublicJwkJson(pub, did));
            realm.setAttribute(DidResource.ATTR_PRIVATE_JWK, DidKeyUtil.toPrivateJwkJson(priv, pub, did));
            LOGGER.info("SSI: auto-generated DID:key {} for realm '{}'", did, realm.getName());
        } catch (Exception e) {
            LOGGER.error("SSI: failed to auto-generate DID:key for realm '{}'", realm.getName(), e);
        }
    }
}
