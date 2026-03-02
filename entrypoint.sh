#!/bin/sh
if [ "${OTEL_ENABLED}" = "true" ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -javaagent:/app/opentelemetry-javaagent.jar"
fi
exec "$@"
