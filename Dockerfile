FROM gradle:9-jdk24-alpine AS build

ARG APP_VERSION=dev

WORKDIR /app

COPY . .

RUN ./gradlew installDist -x test --no-daemon -Papp.version=${APP_VERSION}

FROM alpine:3.21 AS otel-agent
ARG OTEL_AGENT_VERSION=2.14.0
RUN wget -q -O /opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

FROM eclipse-temurin:24-alpine

WORKDIR /app

RUN apk add --no-cache \
    fontconfig \
    curl \
    bash \
    ttf-liberation

RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

COPY --from=build /app/app/build/install/app/ /app/
COPY --from=otel-agent /opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY entrypoint.sh /app/entrypoint.sh

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0" \
    JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED" \
    OTEL_ENABLED=false \
    OTEL_SERVICE_NAME=pdf-ua-api \
    OTEL_LOGS_EXPORTER=none

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD curl -f http://localhost:${PORT:-8080}/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["/app/bin/app"]
