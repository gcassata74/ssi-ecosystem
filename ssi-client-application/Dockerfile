# SSI Client Application
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

# Stage 1: build and pack ssi-client-lib
FROM node:20-alpine AS lib-build

WORKDIR /ssi-client-lib

COPY ssi-client-lib/package*.json ./
RUN npm ci

COPY ssi-client-lib .
RUN npm run build && npm pack

# Stage 2: build Spring Boot app (Angular included via frontend-maven-plugin)
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Mirror the real filesystem layout so file: references in package.json resolve correctly:
#   /workspace/ssi-client-lib/          ← packed tgz
#   /workspace/ssi-client-application/  ← Maven project
COPY --from=lib-build /ssi-client-lib/ssi-issuer-auth-client-0.1.5.tgz ssi-client-lib/ssi-issuer-auth-client-0.1.5.tgz

COPY ssi-client-application/pom.xml ssi-client-application/pom.xml
COPY ssi-client-application/backend/pom.xml ssi-client-application/backend/pom.xml
COPY ssi-client-application/frontend/pom.xml ssi-client-application/frontend/pom.xml

COPY ssi-client-application/backend ssi-client-application/backend
COPY ssi-client-application/frontend ssi-client-application/frontend

# Build frontend first (ng build → dist/), then backend copies the dist into the JAR
RUN cd ssi-client-application && mvn -B clean package -DskipTests

RUN JAR_PATH="$(find ssi-client-application/backend/target -maxdepth 1 -type f -name '*.jar' ! -name '*original*')" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
