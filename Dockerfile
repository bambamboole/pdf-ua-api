FROM gradle:9-jdk24-alpine AS build

WORKDIR /app

COPY . .

RUN ./gradlew installDist -x test --no-daemon

FROM eclipse-temurin:24-alpine

WORKDIR /app

RUN apk add --no-cache \
    fontconfig \
    ttf-liberation

RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

COPY --from=build /app/app/build/install/app/ /app/

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0" \
    JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/bin/app"]
