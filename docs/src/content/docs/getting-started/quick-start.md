---
title: Quick Start
description: Run the API and convert your first document.
---

## Run the API

```bash
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest
```

## Convert HTML to PDF

```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><head><title>Hello</title></head><body><h1>Hello PDF</h1></body></html>"}' \
  --output output.pdf
```

The response body is the binary PDF. See [Converting HTML to PDF](/html-to-pdf/)
for details, or try it live in the browser.
