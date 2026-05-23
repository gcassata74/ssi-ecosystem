# SSI Issuer
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License,
# or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Install parent POM so child modules can resolve it
COPY pom.xml .
RUN mvn -B install -N -DskipTests

# Install ssi-common (required by ssi-issuer)
COPY ssi-common/pom.xml ssi-common/pom.xml
COPY ssi-common/src ssi-common/src
RUN mvn -B -f ssi-common/pom.xml install -DskipTests

# Build ssi-issuer (sibling poms needed for reactor)
COPY ssi-keycloak-provider/pom.xml ssi-keycloak-provider/pom.xml
COPY ssi-verifier/pom.xml ssi-verifier/pom.xml
COPY ssi-verifier/backend/pom.xml ssi-verifier/backend/pom.xml
COPY ssi-issuer/pom.xml ssi-issuer/pom.xml
COPY ssi-issuer/backend/pom.xml ssi-issuer/backend/pom.xml
COPY ssi-issuer/backend/src ssi-issuer/backend/src
COPY ssi-issuer/frontend ssi-issuer/frontend
RUN mvn -B -pl ssi-issuer/backend package -DskipTests

RUN JAR_PATH="$(find ssi-issuer/backend/target -maxdepth 1 -type f -name '*.jar' ! -name '*original*')" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
