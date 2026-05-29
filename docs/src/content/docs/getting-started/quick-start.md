---
title: Quick Start
description: Run the API and convert your first document.
---

This guide gets a PDF out of the API in two steps: start the server, then send it some HTML.

## Run the API

The quickest way to run the API is the published Docker image, which bundles the JVM, the fonts,
and everything else it needs. Start it and expose port `8080`:

```bash
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest
```

The API is now listening on `http://localhost:8080`. By default it runs without authentication
and with rate limiting enabled — see [Authentication](/authentication/) and
[Rate Limiting](/rate-limiting/) to change either.

## Convert your first PDF

Send a complete HTML document to `POST /convert`. The response body is the binary PDF, so write
it straight to a file with `--output`:

```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html lang=\"en\"><head><title>Hello</title></head><body><h1>Hello PDF</h1></body></html>"}' \
  --output output.pdf
```

Open `output.pdf` and you have an accessible PDF/A-3a document. Always include a `lang` attribute
and a `<title>` — both are required for the accessibility tags to be generated.

## Next steps

- Learn which HTML and CSS the renderer supports, and try it live in the browser, on the
  [HTML to PDF](/html/html-to-pdf/) page.
- Build structured documents without hand-writing HTML using [Templates](/templates/structure/).
- Explore every endpoint in the [API Reference](/api/).
