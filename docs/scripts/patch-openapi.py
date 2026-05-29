#!/usr/bin/env python3
"""
Post-process the Inspektor-generated openapi.yaml.

Inspektor only emits application/json / text/plain content types and does not
know about binary or image responses. This script patches the spec in place so
the binary endpoints declare the actual media type and a {string, binary}
schema, and the /schema endpoint gets a richer description.

Drop this step once https://github.com/tabilzad/inspektor/issues/72 ships.
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml


BINARY_SCHEMA = {"type": "string", "format": "binary"}

# Map of (path, method) -> list of content types for the 200 response.
BINARY_RESPONSES = {
    ("/convert", "post"): ["application/pdf"],
    ("/render/template", "post"): ["application/pdf"],
    ("/render", "post"): ["image/png", "image/jpeg"],
}

SCHEMA_DESCRIPTION = (
    "Canonical JSON Schema (Draft 2020-12) for the template rendering payload. "
    "The response is a JSON Schema document describing the Template type accepted "
    "by /render/template, with builder metadata under x-pdfUa."
)


def patch_binary_response(path_item: dict, method: str, content_types: list[str]) -> None:
    response = path_item.get(method, {}).get("responses", {}).get("200")
    if not response:
        return
    response["content"] = {ct: {"schema": dict(BINARY_SCHEMA)} for ct in content_types}


def patch_schema_endpoint(path_item: dict) -> None:
    get_op = path_item.get("get")
    if not get_op:
        return
    get_op["description"] = SCHEMA_DESCRIPTION
    response = get_op.get("responses", {}).get("200")
    if response:
        response["content"] = {
            "application/json": {"schema": {"type": "object"}},
        }


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-openapi.py <openapi.yaml>", file=sys.stderr)
        return 2

    target = Path(sys.argv[1])
    spec = yaml.safe_load(target.read_text())
    paths = spec.get("paths", {})

    for (path, method), content_types in BINARY_RESPONSES.items():
        path_item = paths.get(path)
        if path_item:
            patch_binary_response(path_item, method, content_types)

    schema_path = paths.get("/schema")
    if schema_path:
        patch_schema_endpoint(schema_path)

    target.write_text(yaml.safe_dump(spec, sort_keys=False, allow_unicode=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
