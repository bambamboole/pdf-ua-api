# PDF UA API

A simple HTTP API for converting HTML to PDF with PDF/A-3a accessibility compliance. Built with Ktor and openhtmltopdf.

## Quick Start

```bash
# Run with Docker (from GitHub Container Registry)
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest

# Test the API
curl http://localhost:8080/health
```

## Features

- **HTML to PDF Conversion** - Convert any HTML document to PDF
- **PDF/UA Compliant** - All PDFs are PDF/A-3a accessible by default
- **Bundled Fonts** - Liberation fonts included (no system dependencies)
- **CSS Support** - Full CSS 2.1 styling
- **Validation** - Built-in PDF/A compliance validation

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

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT`   | 8080    | Server port |

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

## Building from source

```bash
# Build Docker image
docker build -t pdf-api .


### Prerequisites for Development

- Java 25
- Gradle

```bash
# Run tests (suppress Java 25 native access warnings)
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
./gradlew test

# Run locally
./gradlew run
```

## License

This project is a proof-of-concept implementation.
