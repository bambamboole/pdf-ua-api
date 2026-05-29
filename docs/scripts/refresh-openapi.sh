#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
./gradlew :app:compileKotlin
cp app/build/resources/main/openapi/openapi.yaml docs/src/openapi/openapi.yaml
echo "Updated docs/src/openapi/openapi.yaml"
