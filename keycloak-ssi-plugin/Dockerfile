FROM maven:3.9-eclipse-temurin-17 AS oidc4vc-builder
WORKDIR /workspace
COPY providers/oidc4vc-keycloak-plugin /workspace/oidc4vc-keycloak-plugin
RUN mvn -B -f oidc4vc-keycloak-plugin/pom.xml clean package

FROM quay.io/keycloak/keycloak:latest

ENV KEYCLOAK_ADMIN=admin \
    KEYCLOAK_ADMIN_PASSWORD=admin

WORKDIR /opt/keycloak

COPY --from=oidc4vc-builder \
    /workspace/oidc4vc-keycloak-plugin/target/oidc4vc-keycloak-plugin-0.1.0-SNAPSHOT.jar \
    /opt/keycloak/providers/oidc4vc-keycloak-plugin.jar

COPY --chown=keycloak:keycloak themes/ /opt/keycloak/themes/
COPY --chown=keycloak:keycloak realm-import/ /opt/keycloak/data/import/

RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev"]
