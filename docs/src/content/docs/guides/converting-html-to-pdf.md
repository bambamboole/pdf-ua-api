---
title: Converting HTML to PDF
description: Convert an HTML document to an accessible PDF/A-3a file.
---

The `POST /convert` endpoint accepts a JSON body with an `html` field and returns a
PDF/A-3a document with PDF/UA accessibility.

## Request

```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html lang=\"en\"><head><title>Invoice</title></head><body><h1>Invoice</h1></body></html>"}' \
  --output invoice.pdf
```

## Notes

- Always set a `lang` attribute and a `<title>` — they are required for accessible output.
- Bundled fonts are available by `font-family`; no system fonts are needed.
- Wide tables paginate automatically.

For the full request/response schema, see the [API Reference](/api/).
