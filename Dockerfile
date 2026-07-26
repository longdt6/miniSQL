# Dockerfile for miniSQL
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Copy pom first so dependency resolution is cached across builds
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source and package
COPY src/ src/
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy the packaged jar
COPY --from=build /src/target/minisql-*.jar app.jar

# Copy entrypoint script for Render PORT binding
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

# Data directory for the catalog/heap files. On Render this is ephemeral
# unless a persistent disk is mounted at /app/data.
RUN mkdir -p /app/data

# Render injects PORT at runtime; default matches application.properties
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/ || exit 1

ENTRYPOINT ["./docker-entrypoint.sh"]
