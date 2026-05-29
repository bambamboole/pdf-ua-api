#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
./gradlew :app:compileKotlin
cp app/build/resources/main/openapi/openapi.yaml docs/openapi/openapi.yaml
# python3 docs/scripts/patch-openapi.py docs/openapi/openapi.yaml
echo "Updated docs/openapi/openapi.yaml"
