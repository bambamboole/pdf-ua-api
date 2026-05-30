---
title: S3 PDF Upload
description: Push the generated document straight to a presigned (S3) URL instead of returning it.
---

Instead of returning the generated document in the response body, the API can upload it directly
to object storage using a **presigned URL** you supply per request. This avoids round-tripping
large files through your application and is handy for serverless callers.

## How it works

Send an `X-Upload-Url` header containing a presigned **PUT** URL on any generating endpoint —
`POST /convert`, `POST /render/url`, `POST /render/template`, or `POST /render`. The API then
`PUT`s the result to that URL with the correct `Content-Type` (`application/pdf`, or
`image/png` / `image/jpeg` for `/render`) and replies `204 No Content` with an empty body. For
PDFs the `X-Document-UUID` response header is still set, so you can correlate the stored
file.

You generate the presigned URL yourself (for example with the AWS SDK's `put_object` presigner),
so the API never needs storage credentials — it only performs the upload.

## Example

```bash
curl -i -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -H "X-Upload-Url: https://my-bucket.s3.amazonaws.com/out.pdf?X-Amz-Algorithm=...&X-Amz-Signature=..." \
  -d '{"html":"<html lang=\"en\"><head><title>Doc</title></head><body><h1>Hi</h1></body></html>"}'
# HTTP/1.1 204 No Content
```

The presigned URL must accept a `PUT` with the matching content type. Omit the header to get the
document back inline as usual.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `UPLOAD_ENABLED` | `true` | Enable or disable the `X-Upload-Url` feature |
| `UPLOAD_TIMEOUT` | `30000` | Upload request timeout in milliseconds |
| `UPLOAD_ALLOWED_DOMAINS` | — | Optional comma-separated allowlist of upload hosts; empty means any public host |

```bash
docker run -p 8080:8080 \
  -e UPLOAD_ALLOWED_DOMAINS="my-bucket.s3.amazonaws.com" \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

## Security

The upload URL is validated with the same SSRF guard as asset fetching: private, loopback, and
link-local addresses are always rejected, and redirects are never followed so they can't bypass
the guard. Set `UPLOAD_ALLOWED_DOMAINS` to restrict uploads to specific hosts.

## Responses

- **`204 No Content`** — the document was uploaded successfully.
- **`400 Bad Request`** — uploads are disabled (`UPLOAD_ENABLED=false`), or the URL is invalid or
  blocked by the SSRF guard / allowlist.
- **`502 Bad Gateway`** — the upload target rejected the `PUT` (non-2xx) or was unreachable.
