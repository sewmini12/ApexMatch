# ==============================================================================
# Stage 1: Build Stage
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy project definition and source code
COPY pom.xml .
COPY src ./src

# Build production Spring Boot executable JAR (skipping tests for image build)
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime Stage
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

# Create a secure non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the generated JAR from the builder stage
COPY --from=builder /build/target/apexmatch-0.0.1-SNAPSHOT.jar /app/app.jar

# Ensure proper permissions
RUN chown -R appuser:appgroup /app

# Run as non-root user
USER appuser

# Expose the default Spring Boot port
EXPOSE 8080

# Configure container-aware JVM flags and execute application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
