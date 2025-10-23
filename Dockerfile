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
