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
- pdf-ua-api's rate limiter is disabled (`RATE_LIMIT_ENABLED=false`) so the load
  test measures raw engine throughput; WeasyPrint has no limiter, so both run
  unthrottled.
- Numbers reflect one machine and one run — re-run locally to reproduce.
