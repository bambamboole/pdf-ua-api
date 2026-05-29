# PDF UA API

A simple HTTP API for converting HTML to PDF with PDF/A-3a accessibility compliance. Built with Ktor and openhtmltopdf.

**Documentation:** [pdf-ua-api.bambamboole.com](https://pdf-ua-api.bambamboole.com) — guides, examples, and the full [API reference](https://pdf-ua-api.bambamboole.com/api).

## Quick Start

```bash
# Run with Docker (from Docker Hub)
docker run -p 8080:8080 bambamboole/pdf-ua-api:latest

# Or from GitHub Container Registry
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest

# Open the Web UI
open http://localhost:8080

# Or use the in-app Swagger UI
open http://localhost:8080/api-docs
```

## Features

- **HTML to PDF Conversion** - Convert any HTML document to PDF/A-3a
- **PDF/UA Compliant** - Full accessibility compliance built-in
- **Thread-Safe** - Handles concurrent requests efficiently
- **Bundled Fonts** - Popular open-source fonts included (no system dependencies)
- **CSS Support** - Full CSS 2.1 styling with automatic table pagination
- **Validation** - Built-in PDF/A compliance validation with veraPDF
- **Flexible Configuration** - Environment variable-based configuration
- **Optional Authentication** - API key (Bearer token) or JWT (RS256 via JWKS)

## API Endpoints

Full schemas and examples live in the [API reference](https://pdf-ua-api.bambamboole.com/api). A short tour:

| Endpoint              | Purpose                                                      |
|-----------------------|--------------------------------------------------------------|
| `POST /convert`              | HTML → PDF/A-3a (binary PDF)                          |
| `POST /convert-and-validate` | HTML → PDF/A-3a + veraPDF validation in one call      |
| `POST /render/template`      | JSON template → PDF/A-3a (binary PDF)                 |
| `POST /render`               | HTML → PNG/JPEG image                                 |
| `POST /validate`             | Validate an existing PDF against PDF/A-3a + PDF/UA-1  |
| `POST /identify`             | Check whether a PDF was produced by this API          |
| `GET  /schema`               | JSON Schema (Draft 2020-12) for `/render/template`    |
| `GET  /health`               | Liveness probe                                        |

### Quick example

```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><head><title>Document</title></head><body><h1>Hello World</h1></body></html>"}' \
  --output output.pdf
```

## PDF/UA Compliance

All generated PDFs are PDF/A-3a compliant with full accessibility support.

### Required HTML Meta Tags

For full compliance, include these meta tags:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Document Title</title>
    <meta name="subject" content="Document subject"/>
    <meta name="description" content="Document description"/>
    <meta name="author" content="Author Name"/>
</head>
<body>
<!-- Your content -->
</body>
</html>
```

## Configuration

### Environment Variables

| Variable           | Default          | Description                                                                                                     |
|--------------------|------------------|-----------------------------------------------------------------------------------------------------------------|
| `PORT`             | `8080`           | HTTP server port                                                                                                |
| `API_KEY`          | (none)           | Optional API key (Bearer token). When set, all conversion and validation endpoints require authentication       |
| `PDF_PRODUCER`     | `pdf-ua-api.com` | PDF producer metadata shown in generated PDFs                                                                   |
| `MAX_REQUEST_SIZE` | `10485760`       | Maximum request size in bytes (default: 10MB)                                                                   |
| `LOG_LEVEL`        | `INFO`           | Logging level: `DEBUG`, `INFO`, `WARN`, or `ERROR`                                                              |

Additional knobs (JWT, [rate limiting](https://pdf-ua-api.bambamboole.com/rate-limiting), CORS, asset/upload allow-lists, OpenTelemetry) are configurable via environment variables — see `src/main/resources/application.yaml` for the full list.

### Configuration Examples

**Production configuration**:

```bash
docker run -p 8080:8080 \
  -e API_KEY=your-secret-key \
  -e PDF_PRODUCER=my-company-v1.0 \
  -e LOG_LEVEL=WARN \
  bambamboole/pdf-ua-api:latest
```

**Development with debug logging**:

```bash
docker run -p 8080:8080 \
  -e LOG_LEVEL=DEBUG \
  -e PDF_PRODUCER=dev-build \
  bambamboole/pdf-ua-api:latest
```

## Authentication

The API supports two optional authentication modes — API key (Bearer token) and JWT (RS256 via JWKS). When either is configured, the conversion and validation endpoints require auth; `/health` remains public. See the [Authentication guide](https://pdf-ua-api.bambamboole.com/authentication) for JWT setup.

### Running with Authentication

```bash
docker run -p 8080:8080 -e API_KEY=your-secret-key bambamboole/pdf-ua-api:latest
```

### Making Authenticated Requests

When authentication is enabled, include the API key as a Bearer token:

```bash
# Convert HTML to PDF with authentication
curl -X POST http://localhost:8080/convert \
  -H "Authorization: Bearer your-secret-key" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><body><h1>Hello</h1></body></html>"}' \
  --output output.pdf

# Validate PDF with authentication
curl -X POST http://localhost:8080/validate \
  -H "Authorization: Bearer your-secret-key" \
  -H "Content-Type: application/pdf" \
  --data-binary @output.pdf
```

**Note:** If neither `API_KEY` nor `JWT_JWKS_URL` is set, the API runs in public mode without authentication.

## HTML Requirements

### Supported

- HTML 4/5 standard tags
- CSS 2.1 (inline and `<style>` tags)
- Tables, lists, basic layout
- Base64 encoded images

### Limitations

- HTML must be well-formed (XHTML-style)
- No external resources (images must be base64)
- CSS 2.1 only (no CSS3 animations, transforms)
- Maximum request size: 10MB

## Observability (OpenTelemetry)

The API ships with the [OpenTelemetry Java agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation) bundled in the Docker image. It auto-instruments Ktor/Netty HTTP spans and JVM metrics with **zero code changes** — just set an environment variable to enable it.

### Enable OpenTelemetry

```bash
docker run -p 8080:8080 \
  -e OTEL_ENABLED=true \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://your-otel-collector:4318 \
  bambamboole/pdf-ua-api:latest
```

### OTel Environment Variables

| Variable                        | Default                 | Description                                  |
|---------------------------------|-------------------------|----------------------------------------------|
| `OTEL_ENABLED`                  | `false`                 | Set to `true` to attach the OTel Java agent  |
| `OTEL_SERVICE_NAME`             | `pdf-ua-api`            | Service name reported to your tracing backend |
| `OTEL_EXPORTER_OTLP_ENDPOINT`  | `http://localhost:4318` | OTLP collector endpoint                      |
| `OTEL_LOGS_EXPORTER`           | `none`                  | Log exporter (disabled by default)           |

All other [OTel Java agent configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/) environment variables are supported.

### Example with Jaeger

```bash
# Start Jaeger
docker run -d --name jaeger -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest

# Start pdf-ua-api with OTel pointing to Jaeger
docker run -p 8080:8080 \
  -e OTEL_ENABLED=true \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4318 \
  bambamboole/pdf-ua-api:latest

# View traces at http://localhost:16686
```

## Building from Source

### Prerequisites

- Java 24
- Gradle (or use included wrapper `./gradlew`)

### Development

```bash
# Clone the repository
git clone https://github.com/bambamboole/pdf-ua-api.git
cd pdf-ua-api

# Run tests (suppress Java native access warnings)
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
./gradlew test

# Run locally with default settings
./gradlew run

# Run with custom configuration
LOG_LEVEL=DEBUG \
PDF_PRODUCER=dev-build \
./gradlew run
```


## License

This project is a proof-of-concept implementation.
