# pdf-ua-api vs WeasyPrint benchmark

Reproducible Docker benchmark comparing `pdf-ua-api` and
[WeasyPrint](https://weasyprint.org/) on HTML→PDF conversion.

## What it measures

- **Throughput / latency** — warmed steady-state req/s and p50/p90/p99, via
  [`oha`](https://github.com/hatoo/oha) at fixed concurrency for a fixed duration.
- **PDF/UA compliance** — every output PDF is validated by pdf-ua-api's own
  veraPDF `/validate` endpoint (PDF/A-3a + PDF/UA-1), so both engines are judged
  by the same validator.
- **File size** — output PDF byte size.

WeasyPrint runs in its best accessibility mode (`pdf/ua-1` variant). A warmup
phase precedes measurement so JVM cold-start does not skew the comparison — this
is disclosed, not hidden.

## Run it

```bash
cd benchmark
make benchmark
```

Override defaults: `CONCURRENCY=40 DURATION=60s WARMUP=100 make benchmark`.

Results are written to `results/latest.json`, which the docs page renders.

## Fairness notes

- Both engines run as single-worker HTTP services in Docker on the same host.
- Identical HTML input per document; corpus uses only CSS both engines support.
- Both containers ship Liberation Sans, so the engines embed the same subsetted
  typeface and file size isn't skewed by font choice.
- pdf-ua-api's rate limiter is disabled (`RATE_LIMIT_ENABLED=false`) so the load
  test measures raw engine throughput; WeasyPrint has no limiter, so both run
  unthrottled.
- Numbers reflect one machine and one run — re-run locally to reproduce.

## On file size

pdf-ua-api produces larger PDFs, but not because WeasyPrint skips font embedding —
both embed subsetted fonts. pdf-ua-api's PDF/A-3a output carries an ICC color
profile, a PDF/A output intent, and a full accessibility tag tree in one file.
WeasyPrint 63.1 can emit either an archival file (`pdf/a-3b`, with the ICC
profile) or an accessible one (`pdf/ua-1`, tagged), but not both at once — so its
files are smaller because they contain less. The benchmark runs WeasyPrint in
`pdf/ua-1`, the fair match for a PDF/UA API.
