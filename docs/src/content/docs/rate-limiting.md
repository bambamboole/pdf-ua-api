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
