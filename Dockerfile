# Multi-stage build for minimal image size
# Stage 1: Build the application
FROM gradle:9-jdk25-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY buildSrc/ buildSrc/

# Copy application source
COPY app/ app/

# Build the application (skip tests for faster builds) and create distribution
RUN ./gradlew installDist -x test --no-daemon

# Stage 2: Create minimal runtime image
FROM eclipse-temurin:25-alpine

# Install required runtime dependencies
# Note: Alpine JRE already includes minimal dependencies
RUN apk add --no-cache \
    fontconfig \
    ttf-liberation

# Create app user for security (don't run as root)
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Set working directory
WORKDIR /app

# Copy application distribution from builder stage
COPY --from=builder /app/app/build/install/app/ /app/

# Change ownership to app user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Set JVM options for container environment and Java 25 compatibility
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0" \
    JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application using the start script
ENTRYPOINT ["/app/bin/app"]
