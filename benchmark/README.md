# pdf-ua-api vs WeasyPrint vs Gotenberg benchmark

Docker benchmark comparing `pdf-ua-api`,
[WeasyPrint](https://weasyprint.org/), and
[Gotenberg Chromium](https://gotenberg.dev/docs/convert-with-chromium/convert-html-to-pdf)
plus [Gotenberg LibreOffice](https://gotenberg.dev/docs/convert-with-libreoffice/convert-to-pdf)
on HTML-to-PDF conversion over HTTP.

## What it measures

- **Throughput / latency** — warmed steady-state req/s and p50/p90/p99, via
  [`oha`](https://github.com/hatoo/oha) at fixed concurrency for a fixed duration.
- **PDF/UA compliance** — every output PDF is validated by pdf-ua-api's own
  veraPDF `/validate` endpoint, so all engines are judged by the same validator.
- **File size** — output PDF byte size.

WeasyPrint is run with its `pdf/ua-1` variant. Gotenberg is measured through
both its Chromium HTML converter and its LibreOffice converter. Chromium gets CSS
page sizes and print backgrounds enabled. Each timed run follows a warmup phase.

The corpus covers three shapes: a small text document, a medium invoice, and a
large mixed report with paragraphs, figures, callouts, and a limited number of
tables. The large document is not a table stress test.

## Run it

```bash
cd benchmark
make benchmark
```

Override defaults with `CONCURRENCY=40 DURATION=60s WARMUP=100 make benchmark`.
The harness sends `embedColorProfile=false` to pdf-ua-api by default so file
size is compared with tags enabled and without the PDF/A output intent. Set
`PDF_UA_EMBED_COLOR_PROFILE=true` to benchmark the normal API default.

Results are written to `results/latest.json` and rendered by the docs benchmark
page.

## Fairness notes

- All engines run as HTTP services in Docker on the same host.
- Identical HTML input per document.
- The containers ship Liberation Sans, so the engines embed the same subsetted
  typeface and file size isn't skewed by font choice.
- Gotenberg Chromium receives the same HTML as an `index.html` multipart upload,
  with `preferCssPageSize=true` and `printBackground=true`.
- Gotenberg LibreOffice receives the same HTML as a `document.html` multipart
  upload through the LibreOffice conversion route.
- pdf-ua-api's rate limiter is disabled (`RATE_LIMIT_ENABLED=false`) so the load
  test measures raw engine throughput; the comparison engines have no limiter, so
  all engines run unthrottled.
- Numbers reflect one machine and one run.

## On file size

pdf-ua-api's normal API default produces larger PDFs, but not because WeasyPrint
skips font embedding. The benchmark containers embed subsetted Liberation Sans.
The default pdf-ua-api output carries an ICC color profile, a PDF/A output
intent, and a full accessibility tag tree in one file. WeasyPrint 63.1 can emit
either an archival file (`pdf/a-3b`, with the ICC profile) or an accessible one
(`pdf/ua-1`, tagged), but not both at once.

For the published comparison the runner disables pdf-ua-api's color profile with
`embedColorProfile=false`, which also skips PDF/A conformance setup and leaves
PDF/UA tagging enabled. That compares pdf-ua-api and WeasyPrint in their
accessible output mode while keeping the Gotenberg rows as rendering baselines.
Set
`PDF_UA_EMBED_COLOR_PROFILE=true` to measure pdf-ua-api's default combined
PDF/A + PDF/UA output instead.
