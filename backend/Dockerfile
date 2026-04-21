# ============================================================
# Backend Dockerfile — multi-stage build
#
# Stage 1 (build): Maven compiles and packages the JAR.
#   Copying pom.xml first lets Docker cache the dependency
#   layer so re-builds only re-run when source changes.
#
# Stage 2 (runtime): Slim JRE image — no compiler, no Maven,
#   no build tools shipped to production.
# ============================================================

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Resolve dependencies as a separate cached layer.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and package (tests skipped — run them in CI).
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot listens on 8080 by default.
EXPOSE 8080

# All configuration is injected via environment variables
# (see docker-compose.yml or application-sample.yml).
ENTRYPOINT ["java", "-jar", "app.jar"]
