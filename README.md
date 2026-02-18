# PDF UA API

A simple HTTP API for converting HTML to PDF with PDF/A-3a accessibility compliance. Built with Ktor and openhtmltopdf.

## Quick Start

```bash
# Run with Docker (from Docker Hub)
docker run -p 8080:8080 bambamboole/pdf-ua-api:latest

# Or from GitHub Container Registry
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest

# Open the Web UI
open http://localhost:8080
```

## Features

- **HTML to PDF Conversion** - Convert any HTML document to PDF/A-3a
- **PDF/UA Compliant** - Full accessibility compliance built-in
- **Thread-Safe** - Handles concurrent requests efficiently
- **Bundled Fonts** - Liberation fonts included (no system dependencies)
- **CSS Support** - Full CSS 2.1 styling with automatic table pagination
- **Validation** - Built-in PDF/A compliance validation with veraPDF
- **Flexible Configuration** - Environment variable-based configuration
- **Optional Authentication** - API key support via Bearer token
- **API-Only Mode** - Can run without web UI for production deployments
- **OpenAPI Documentation** - Auto-generated OpenAPI 3.0 spec with Swagger UI

## API Documentation

Interactive API documentation is available at:

```bash
# Swagger UI (interactive API docs)
http://localhost:8080/docs

# OpenAPI specification (YAML)
http://localhost:8080/docs/documentation.yaml
```

The OpenAPI specification provides a complete description of all API endpoints, request/response schemas, and authentication requirements. You can use this spec with any OpenAPI-compatible tool (Postman, Insomnia, etc.).

## API Endpoints

### Health Check

```bash
GET /health
```

**Response:**

```json
{
  "status": "ok"
}
```

### Convert HTML to PDF

```bash
POST /convert
Content-Type: application/json

{
  "html": "<html>...</html>"
}
```

**Response:** Binary PDF data

**Example:**

```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><head><title>Document</title></head><body><h1>Hello World</h1><p>This is a test PDF.</p></body></html>"}' \
  --output output.pdf
```

### Validate PDF

```bash
POST /validate
Content-Type: application/pdf

<PDF binary data>
```

**Response:**

```json
{
  "isCompliant": true,
  "flavour": "3a",
  "totalChecks": 0,
  "failedChecks": 0,
  "passedChecks": 0,
  "failures": []
}
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
| `API_KEY`          | (none)           | Optional API key for authentication (Bearer token). When set, `/convert` and `/validate` require authentication |
| `WEB_UI_ENABLED`   | `true`           | Enable web UI at `/`. Set to `false` for API-only mode                                                          |
| `PDF_PRODUCER`     | `pdf-ua-api.com` | PDF producer metadata shown in generated PDFs                                                                   |
| `MAX_REQUEST_SIZE` | `10485760`       | Maximum request size in bytes (default: 10MB)                                                                   |
| `LOG_LEVEL`        | `INFO`           | Logging level: `DEBUG`, `INFO`, `WARN`, or `ERROR`                                                              |

### Configuration Examples

**API-only mode (no web UI)**:

```bash
docker run -p 8080:8080 -e WEB_UI_ENABLED=false bambamboole/pdf-ua-api:latest
```

**Production configuration**:

```bash
docker run -p 8080:8080 \
  -e API_KEY=your-secret-key \
  -e WEB_UI_ENABLED=false \
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

The API supports optional API key authentication. When configured, the `/convert` and `/validate` endpoints require a
valid API key, while `/health` remains public.

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

**Note:** If `API_KEY` is not set, the API runs in public mode without authentication.

## HTML Requirements

### ✅ Supported

- HTML 4/5 standard tags
- CSS 2.1 (inline and `<style>` tags)
- Tables, lists, basic layout
- Base64 encoded images

### ⚠️ Limitations

- HTML must be well-formed (XHTML-style)
- No external resources (images must be base64)
- CSS 2.1 only (no CSS3 animations, transforms)
- Maximum request size: 10MB

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
WEB_UI_ENABLED=false \
LOG_LEVEL=DEBUG \
PDF_PRODUCER=dev-build \
./gradlew run
```


## License

This project is a proof-of-concept implementation.
