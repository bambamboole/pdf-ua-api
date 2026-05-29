---
title: Rate Limiting
description: Per-IP and global request limits on the expensive endpoints.
---

The conversion/rendering/validation endpoints are rate limited with two token buckets — one per
client IP and one global — both refilling over a fixed window. Requests over either limit get
`429 Too Many Requests`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `RATE_LIMIT_ENABLED` | `true` | Turn rate limiting on/off |
| `RATE_LIMIT_PER_IP` | `20` | Max requests per client IP per window |
| `RATE_LIMIT_GLOBAL` | `200` | Max requests across the instance per window |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Refill window for both buckets |
| `RATE_LIMIT_TRUST_FORWARDED_FOR` | `false` | Resolve the client IP from `X-Forwarded-*` |

Only enable `RATE_LIMIT_TRUST_FORWARDED_FOR` when running behind a trusted reverse proxy —
otherwise clients can spoof their IP via the header and bypass the per-IP limit.

## Running with Docker

Tune the limits via environment variables:

```bash
docker run -p 8080:8080 \
  -e RATE_LIMIT_PER_IP=60 \
  -e RATE_LIMIT_GLOBAL=600 \
  -e RATE_LIMIT_WINDOW_SECONDS=60 \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

Disable rate limiting entirely (e.g. a trusted internal deployment):

```bash
docker run -p 8080:8080 \
  -e RATE_LIMIT_ENABLED=false \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

Behind a trusted reverse proxy, key the per-IP limit on the real client address:

```bash
docker run -p 8080:8080 \
  -e RATE_LIMIT_TRUST_FORWARDED_FOR=true \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

## When a limit is hit

Requests over either bucket receive `429 Too Many Requests` with a `Retry-After` header telling
the client how long to wait before retrying. Stay under both the per-IP and global limits to
avoid throttling.
