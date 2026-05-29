---
title: Authentication
description: Protect the API with a JWT (JWKS) or a static API key.
---

Authentication is optional and configured by environment variables. Exactly one mechanism is
active at a time:

1. **Static API Key** — active when `API_KEY` is set (and JWT is not).
2. **JWT (JWKS)** — active when `JWT_ISSUER` and `JWT_JWKS_URL` are both set.
3. **Public** — when neither is configured, the API is open.

JWT takes precedence over the API key when both are configured. Authentication guards the
conversion, rendering, validation, and identification endpoints; `/health`, the template schema,
and the API docs stay public. Failed or missing credentials return `401`.

## API KEY


Run the container with the `API_KEY` set:

```bash
docker run -p 8080:8080 \
  -e API_KEY="super-secret-key" \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

Then send it as a Bearer token:

```bash
curl -X POST http://localhost:8080/convert \
  -H "Authorization: Bearer super-secret-key" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html lang=\"en\"><head><title>Doc</title></head><body><h1>Hi</h1></body></html>"}' \
  --output out.pdf
```


## JWT (JWKS)

The API only **verifies** tokens; it never issues them. Configure:

- `JWT_ISSUER` — expected `iss` and the JWKS issuer
- `JWT_JWKS_URL` — the issuer's JWKS endpoint (public keys, RS256)
- `JWT_AUDIENCE` — optional; when set, the `aud` claim must match

The signature, issuer, and expiry are always checked; the audience is checked only when
`JWT_AUDIENCE` is set. Send the token as `Authorization: Bearer <token>`.

Run the container configured to verify tokens from your issuer:

```bash
docker run -p 8080:8080 \
  -e JWT_ISSUER="https://auth.example.com/" \
  -e JWT_JWKS_URL="https://auth.example.com/.well-known/jwks.json" \
  -e JWT_AUDIENCE="pdf-ua-api" \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

Omit `JWT_AUDIENCE` to skip the audience check. The API reaches the JWKS URL to fetch the
issuer's public keys, so the container needs network access to it.

Then send it as a Bearer token:

```bash
curl -X POST http://localhost:8080/convert \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html lang=\"en\"><head><title>Doc</title></head><body><h1>Hi</h1></body></html>"}' \
  --output out.pdf
```

## Rejected requests

A missing or invalid credential returns `401 Unauthorized` and the request never reaches the
renderer:

```bash
curl -i -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<h1>Hi</h1>"}'
# HTTP/1.1 401 Unauthorized
```

The public endpoints (`/health`, the template schema, and the API docs) stay reachable without
credentials.
