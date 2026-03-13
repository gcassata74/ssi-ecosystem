# SSI Issuer Verifier
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

# Copy Maven configuration to take advantage of build caching
COPY pom.xml .

# Copy the full project (frontend assets are built via Maven)
COPY src src
COPY frontend frontend
COPY META-INF META-INF
COPY samples samples
COPY idp-metadata.xml idp-metadata.xml
COPY Makefile Makefile

# Build the combined Spring Boot + Angular application
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

# Preserve only the executable JAR for the runtime image
RUN JAR_PATH="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*original*')" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java","-jar","app.jar"]
