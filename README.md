# PDF API

A simple, lightweight HTTP API for converting HTML to PDF documents. Built with Ktor and openhtmltopdf.

## Features

- 🚀 **Simple REST API** - Three endpoints (health, convert, validate)
- 📄 **HTML to PDF Conversion** - Convert any HTML document to PDF
- ♿ **PDF/UA Compliant** - All PDFs are PDF/A-3a accessible by default
- 🎨 **CSS Support** - Full CSS 2.1 styling support
- ⚡ **Fast & Lightweight** - Efficient conversion with minimal overhead
- 🧪 **Well-Tested** - Comprehensive integration tests with PDF validation
- 🔧 **Easy to Deploy** - Single JAR with bundled fonts, no external dependencies

## Quick Start

### Prerequisites

- Java 21 or higher (tested with Java 25)
- Gradle (wrapper included)

**Note for Java 25 users:** To suppress native access warnings from Gradle infrastructure, use:
```bash
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
```
Or use the provided wrapper script: `./gradlew-clean` instead of `./gradlew`

### Build and Run

```bash
# Build the project
./gradlew-clean build
# Or with environment variable:
# export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
# ./gradlew build

# Run the server
./gradlew-clean run

# Server starts on http://localhost:8080
```

### Using Docker

**Quick Start:**
```bash
# Build and run with docker-compose
docker-compose up

# Or build manually
./docker-build.sh

# Run manually
docker run -p 8080:8080 pdf-api
```

**Features:**
- ✅ Multi-stage build (minimal image size ~150MB)
- ✅ Non-root user for security
- ✅ Health check included
- ✅ Bundled Liberation fonts
- ✅ Alpine-based (small footprint)
- ✅ JRE 21 (optimized for containers)

**Advanced Usage:**
```bash
# Build with custom tag
./docker-build.sh v1.0.0

# Run with custom port and memory limits
docker run -p 3000:8080 \
  -e PORT=8080 \
  -m 512m \
  --cpus="0.5" \
  pdf-api

# Run in background
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

## API Endpoints

### 1. Health Check

Check if the service is running.

**Request:**
```bash
GET /health
```

**Response:**
```json
{
  "status": "ok"
}
```

**Example:**
```bash
curl http://localhost:8080/health
```

---

### 2. Convert HTML to PDF

Convert an HTML document to PDF format.

**Request:**
```bash
POST /convert
Content-Type: application/json

{
  "html": "<html>...</html>"
}
```

**Response:**
- **Content-Type:** `application/pdf`
- **Content-Disposition:** `attachment; filename="output.pdf"`
- **Body:** Binary PDF data

**Success Example:**
```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><body><h1>Hello PDF!</h1></body></html>"}' \
  --output output.pdf
```

**With CSS Styling:**
```bash
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d @request.json \
  --output styled.pdf
```

Where `request.json` contains:
```json
{
  "html": "<!DOCTYPE html><html><head><style>body { font-family: Arial; margin: 40px; } h1 { color: #2c3e50; }</style></head><body><h1>Styled Document</h1><p>This document has CSS styles.</p></body></html>"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "HTML content cannot be empty"
}
```

**Error Response (500 Internal Server Error):**
```json
{
  "error": "Failed to convert HTML to PDF: <error message>"
}
```

---

### 3. Validate PDF

Validate a PDF document for PDF/A compliance using veraPDF.

**Request:**
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

**Example:**
```bash
curl -X POST http://localhost:8080/validate \
  -H "Content-Type: application/pdf" \
  --data-binary @document.pdf
```

## Configuration

### Environment Variables

- `PORT` - Server port (default: 8080)

### Application Configuration

Edit `app/src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    server {
        maxRequestSize = 10485760  # 10MB limit
    }
}
```

## HTML Requirements & Limitations

### ✅ Supported Features

- **HTML 4/5** - Standard HTML tags
- **CSS 2.1** - Full CSS 2.1 specification
- **Inline styles** - Style attributes on elements
- **External stylesheets** - Via `<style>` tags
- **Tables** - Full table layout support
- **Lists** - Ordered and unordered lists
- **Images** - Base64 encoded images only (no external URLs)

### ⚠️ Important Limitations

1. **Well-formed HTML Required**
   - HTML must be valid XHTML (properly closed tags)
   - Use `<!DOCTYPE html>` for best results

2. **No External Resources**
   - Images must be base64 encoded
   - External CSS/JS files are not loaded
   - Base URL is set to `file:///` for local resources

3. **Bundled Fonts**
   - Liberation fonts included (Sans, Serif, Mono)
   - Metrically compatible with Arial, Times New Roman, Courier
   - All PDFs generated with PDF/UA accessibility compliance
   - No system font dependencies required

4. **Request Size Limit**
   - Maximum request size: 10MB
   - For larger documents, consider pagination

5. **CSS Limitations**
   - CSS 2.1 only (no CSS3 features like animations, transforms)
   - Limited support for advanced selectors

## PDF/UA Accessibility Compliance

All generated PDFs are **PDF/A-3a compliant** with full accessibility support:

### What's Included
- ✅ **PDF/UA Accessibility** - Universal accessibility standard (ISO 14289-1)
- ✅ **PDF/A-3a Archival** - Long-term preservation standard
- ✅ **Embedded Fonts** - Liberation fonts bundled (no system dependencies)
- ✅ **sRGB Color Profile** - Device-independent color space
- ✅ **Document Metadata** - Extracted from HTML meta tags

### Required HTML Meta Tags

For full PDF/UA compliance, include these meta tags in your HTML:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Document Title</title>
    <meta name="subject" content="Document subject" />
    <meta name="description" content="Document description" />
    <meta name="author" content="Author Name" />
</head>
<body>
    <!-- Your content -->
</body>
</html>
```

### Validation

All generated PDFs can be validated using the `/validate` endpoint:

```bash
curl -X POST http://localhost:8080/validate \
  -H "Content-Type: application/pdf" \
  --data-binary @your-document.pdf
```

## Architecture

### Project Structure

```
pdf-api/
├── app/
│   └── src/
│       ├── main/kotlin/bambamboole/pdf/api/
│       │   ├── Application.kt          # Ktor server setup
│       │   ├── models/
│       │   │   └── ConvertRequest.kt   # Request DTO
│       │   ├── routes/
│       │   │   ├── HealthRoutes.kt     # Health endpoint
│       │   │   └── ConvertRoutes.kt    # Convert endpoint
│       │   └── services/
│       │       └── PdfService.kt       # PDF conversion logic
│       ├── resources/
│       │   ├── application.conf        # Ktor configuration
│       │   └── logback.xml             # Logging configuration
│       └── test/kotlin/bambamboole/pdf/api/
│           └── ApplicationTest.kt      # Integration tests
├── gradle/
│   └── libs.versions.toml             # Version catalog
└── README.md
```

### Technology Stack

- **Kotlin 2.3.0** - Modern JVM language
- **Ktor 3.1.1** - Lightweight web framework
  - Netty engine for high performance
  - Content negotiation for JSON
  - Call logging for observability
  - Status pages for error handling
- **openhtmltopdf 1.1.37** - HTML to PDF conversion
  - PDFBox renderer for PDF generation
  - PDF/UA accessibility support
  - PDF/A-3a compliance
- **veraPDF 1.28.1** - PDF/A validation
- **Liberation Fonts 2.1.5** - Bundled fonts (SIL OFL 1.1)
- **kotlinx.serialization** - JSON serialization
- **Logback 1.5.16** - Logging framework

## Development

### Running Tests

```bash
# Run all tests (no warnings)
./gradlew-clean test

# Or with environment variable
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
./gradlew test

# Run specific test
./gradlew-clean test --tests "ApplicationTest.testHealthEndpoint"
```

### Building Distribution

```bash
# Build distribution archives
./gradlew distTar
./gradlew distZip

# Archives created in: app/build/distributions/
```

### Running in Production

**Option 1: Docker (Recommended)**
```bash
# Production deployment with docker-compose
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f pdf-api
```

**Option 2: Direct JAR execution**
```bash
# Build executable JAR
./gradlew-clean build

# Run the JAR
java -jar app/build/libs/app.jar

# With custom port and JVM options
PORT=3000 java -Xmx512m -jar app/build/libs/app.jar
```

**Production JVM Options:**
```bash
java -XX:+UseContainerSupport \
     -XX:MaxRAMPercentage=75.0 \
     -XX:+UseG1GC \
     -Xlog:gc* \
     -jar app.jar
```

## Testing the API

### Using curl

```bash
# Health check
curl http://localhost:8080/health

# Simple conversion
curl -X POST http://localhost:8080/convert \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><body><h1>Test</h1></body></html>"}' \
  --output test.pdf

# Verify PDF
file test.pdf
# Output: test.pdf: PDF document, version 1.6
```

### Using Postman

1. **Import Collection:**
   - Method: POST
   - URL: `http://localhost:8080/convert`
   - Headers: `Content-Type: application/json`
   - Body (raw JSON):
     ```json
     {
       "html": "<html><body><h1>Test from Postman</h1></body></html>"
     }
     ```

2. **Send & Save Response:**
   - Click "Send"
   - Click "Save Response" → "Save to a file"
   - Open the PDF file

### Sample HTML Documents

**Simple Document:**
```html
<!DOCTYPE html>
<html>
<head>
    <title>Invoice</title>
</head>
<body>
    <h1>Invoice #12345</h1>
    <p>Date: 2026-02-06</p>
    <table>
        <tr><th>Item</th><th>Price</th></tr>
        <tr><td>Service</td><td>$100.00</td></tr>
    </table>
</body>
</html>
```

**Styled Document:**
```html
<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 40px;
            line-height: 1.6;
        }
        h1 {
            color: #2c3e50;
            border-bottom: 2px solid #3498db;
            padding-bottom: 10px;
        }
        .highlight {
            background-color: #f1c40f;
            padding: 2px 5px;
        }
    </style>
</head>
<body>
    <h1>Professional Report</h1>
    <p>This is a <span class="highlight">highlighted</span> section.</p>
</body>
</html>
```

## Troubleshooting

### Common Issues

**1. PDF Generation Fails with Malformed HTML**
- Ensure HTML is well-formed (all tags closed)
- Use HTML validators: https://validator.w3.org/
- Try wrapping content in proper DOCTYPE and html tags

**2. Fonts Not Displaying Correctly**
- Use standard web-safe fonts (Arial, Times New Roman, Courier)
- For custom fonts, they must be installed on the server

**3. Images Not Showing**
- Convert images to base64:
  ```html
  <img src="data:image/png;base64,iVBORw0KGgo..." />
  ```
- External URLs are not supported

**4. Large HTML Takes Too Long**
- Check request size (max 10MB)
- Simplify CSS (remove unused styles)
- Optimize images (reduce resolution)

**5. Server Won't Start**
- Check if port 8080 is available: `lsof -i :8080`
- Change port: `PORT=3000 ./gradlew run`

## Performance Considerations

- **First Request Latency:** Initial PDF generation may take 1-2 seconds due to font cache building
- **Subsequent Requests:** ~100-200ms for simple documents
- **Concurrent Requests:** Netty handles multiple connections efficiently
- **Memory Usage:** ~100-200MB base + ~10-50MB per concurrent request

## Security Considerations

⚠️ **Important:** This is a POC implementation. For production use:

1. **Add Authentication** - Protect endpoints with API keys or OAuth
2. **Rate Limiting** - Prevent abuse with rate limiting middleware
3. **Input Validation** - Sanitize HTML input to prevent XSS
4. **Resource Limits** - Enforce stricter memory/CPU limits
5. **HTTPS Only** - Use TLS for all connections
6. **Content Security** - Validate HTML doesn't contain malicious scripts

## License

This project is a proof-of-concept implementation.

## Contributing

This is a POC project. For production use, consider:
- Adding authentication/authorization
- Implementing rate limiting
- Adding metrics and monitoring
- Setting up CI/CD pipelines
- Containerizing with Docker
- Adding API documentation (OpenAPI/Swagger)

## Support

For issues and questions:
- Check the [troubleshooting section](#troubleshooting)
- Review the [limitations](#important-limitations)
- Test with the provided [examples](#testing-the-api)
