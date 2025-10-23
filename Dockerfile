FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Copy Maven project files first to leverage Docker layer caching
COPY pom.xml .
COPY backend/pom.xml backend/
COPY frontend/pom.xml frontend/

# Copy the remaining source code
COPY backend backend
COPY frontend frontend

# Build the Spring Boot backend (Angular assets are produced via the Maven lifecycle)
RUN --mount=type=cache,target=/root/.m2 mvn -pl backend -am -B clean package -DskipTests

# Keep only the runnable JAR for the runtime image
RUN JAR_PATH="$(find backend/target -maxdepth 1 -type f -name '*.jar' ! -name '*original*')" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 9091

ENTRYPOINT ["java","-jar","app.jar"]
