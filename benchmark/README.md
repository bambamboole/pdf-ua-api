# pdf-ua-api vs WeasyPrint vs Gotenberg benchmark

Reproducible Docker benchmark comparing `pdf-ua-api`,
[WeasyPrint](https://weasyprint.org/), and
[Gotenberg Chromium](https://gotenberg.dev/docs/convert-with-chromium/convert-html-to-pdf)
on HTML→PDF conversion.

## What it measures

- **Throughput / latency** — warmed steady-state req/s and p50/p90/p99, via
  [`oha`](https://github.com/hatoo/oha) at fixed concurrency for a fixed duration.
- **PDF/UA compliance** — every output PDF is validated by pdf-ua-api's own
  veraPDF `/validate` endpoint, so all engines are judged by the same validator.
- **File size** — output PDF byte size.

WeasyPrint runs in its best accessibility mode (`pdf/ua-1` variant). Gotenberg
runs the Chromium HTML converter with CSS page sizes and print backgrounds
enabled. A warmup phase precedes measurement so cold-start does not skew the
comparison — this is disclosed, not hidden.

## Run it

```bash
cd benchmark
make benchmark
```

Override defaults: `CONCURRENCY=40 DURATION=60s WARMUP=100 make benchmark`.
The harness sends `embedColorProfile=false` to pdf-ua-api by default so file
size is compared in PDF/UA mode without the PDF/A output intent. Set
`PDF_UA_EMBED_COLOR_PROFILE=true` to benchmark the normal archival default.

Results are written to `results/latest.json`, which the docs page renders.

## Fairness notes

- All engines run as HTTP services in Docker on the same host.
- Identical HTML input per document; corpus uses CSS all engines support.
- The containers ship Liberation Sans, so the engines embed the same subsetted
  typeface and file size isn't skewed by font choice.
- Gotenberg receives the same HTML as an `index.html` multipart upload, with
  `preferCssPageSize=true` and `printBackground=true`.
- pdf-ua-api's rate limiter is disabled (`RATE_LIMIT_ENABLED=false`) so the load
  test measures raw engine throughput; the comparison engines have no limiter, so
  all engines run unthrottled.
- Numbers reflect one machine and one run — re-run locally to reproduce.

## On file size

pdf-ua-api's normal API default produces larger PDFs, but not because WeasyPrint
skips font embedding — the engines embed subsetted fonts. The default PDF/A-3a
output carries an ICC color profile, a PDF/A output intent, and a full
accessibility tag tree in one file. WeasyPrint 63.1 can emit either an archival
file (`pdf/a-3b`, with the ICC profile) or an accessible one (`pdf/ua-1`,
tagged), but not both at once.

For the published comparison the runner disables pdf-ua-api's color profile with
`embedColorProfile=false`, which also skips PDF/A conformance setup and leaves
PDF/UA tagging enabled. That compares pdf-ua-api and WeasyPrint in their
accessible output mode while showing where Chromium-only rendering lands. Set
`PDF_UA_EMBED_COLOR_PROFILE=true` to measure pdf-ua-api's default combined
PDF/A + PDF/UA output instead.
