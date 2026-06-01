# Render HTML Endpoint and JSON Validation Response Design

## Goal

Align HTML-to-PDF conversion with the render endpoint namespace and replace the dedicated convert-and-validate endpoint with content negotiation on the render endpoints.

The public PDF render endpoints are:

- `POST /render/html`
- `POST /render/url`
- `POST /render/template`

The old endpoints are removed directly:

- `POST /convert`
- `POST /convert-and-validate`

No compatibility aliases are provided.

## Public API

`POST /render/html` accepts the same JSON payload shape that `/convert` accepts today:

```json
{
  "html": "<html>...</html>",
  "baseUrl": "https://example.com/",
  "attachments": []
}
```

The Kotlin DTO should be renamed from `ConvertRequest` to `RenderHtmlRequest`. JSON field names stay unchanged.

All three PDF render endpoints use the same response negotiation:

- Missing `Accept`, `Accept: */*`, or non-explicit JSON accepts return the binary PDF.
- Explicit `Accept: application/json` returns validation and a base64-encoded PDF.
- `X-Upload-Url` uploads the generated PDF and returns `204 No Content`.
- `Accept: application/json` together with `X-Upload-Url` returns `400 Bad Request`.

The JSON response keeps the existing `/convert-and-validate` field names:

```json
{
  "validation": {},
  "pdf": "base64-encoded-pdf"
}
```

`X-Document-UUID` is returned for both binary PDF and JSON responses.

## Internal Design

PDF route handlers should only create a `PdfResult`. They should not branch on response mode.

Add a PDF-specific helper in `UploadResponse.kt`, for example:

```kotlin
suspend fun RoutingContext.respondPdfOrUpload(
    result: PdfResult,
    uploader: DocumentUploader?,
)
```

The helper owns:

- detecting `X-Upload-Url`
- detecting explicit `Accept: application/json`
- rejecting JSON response mode combined with upload mode
- setting binary PDF headers
- running `PdfValidator.validatePdf(result.bytes)` only for JSON responses
- base64 encoding only for JSON responses
- setting `X-Document-UUID`

Keep the existing generic `respondDocumentOrUpload` for image rendering. Image rendering is not part of the JSON validation behavior.

Move all PDF rendering routes into `Render.kt`:

- `/render/html` receives `RenderHtmlRequest`, validates `html` and optional `baseUrl`, calls `PdfRenderer.convertHtmlToPdf`, then calls `respondPdfOrUpload`.
- `/render/template` keeps existing template validation and HTML rendering, then calls `respondPdfOrUpload`.
- `/render/url` keeps existing HTML fetching and uses the fetched final URL as `baseUrl`, then calls `respondPdfOrUpload`.

Remove `Convert.kt` and `ConvertAndValidate.kt` after route wiring and tests are updated.

## OpenAPI and Docs

Update API documentation to describe `/render/html` as the direct HTML-to-PDF endpoint. Remove `/convert-and-validate`.

Update OpenAPI patching in `build.gradle.kts`:

- remove `/convert`
- add `/render/html`
- keep `/render/template`
- include `/render/url`
- represent both `application/pdf` and `application/json` success responses for PDF render endpoints where the generator patch supports it

Refresh `docs/openapi/openapi.json` through the existing build flow if it changes as part of generation.

## Tests

Replace `/convert` test usage with `/render/html`.

Convert the old `/convert-and-validate` tests into response negotiation tests across render endpoints.

Required assertions:

- `/render/html` returns binary `application/pdf` by default.
- `/render/html` with explicit `Accept: application/json` returns `validation` and `pdf`.
- `/render/url` with explicit `Accept: application/json` returns `validation` and `pdf`.
- `/render/template` with explicit `Accept: application/json` returns `validation` and `pdf`.
- Missing `Accept` or `Accept: */*` returns binary PDF.
- Explicit `Accept: application/json` plus `X-Upload-Url` returns `400 Bad Request`.
- `/convert` is no longer registered.
- `/convert-and-validate` is no longer registered.

Update setup tests that currently call `/convert`, including validation, identify, custom producer, rate-limit, CORS, JWT, and upload tests.

## Verification

Run focused route coverage first:

```bash
./gradlew test --tests '*Render*RoutesTest' --tests '*UploadRoutesTest' --tests '*ValidationRoutesTest' --tests '*IdentifyRoutesTest'
```

Then run the CI-equivalent checks:

```bash
./gradlew spotlessCheck detekt --no-daemon
./gradlew test --no-daemon
```

## Decisions

- `/convert` is removed directly.
- `/convert-and-validate` is removed directly.
- JSON validation response mode requires an explicit `Accept: application/json`.
- Upload mode and JSON response mode are mutually exclusive.
- The JSON response field names are `validation` and `pdf`.
