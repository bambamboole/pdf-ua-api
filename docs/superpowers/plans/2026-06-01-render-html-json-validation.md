# Render HTML JSON Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `/convert` and `/convert-and-validate` with `/render/html` plus explicit `Accept: application/json` validation responses across PDF render endpoints.

**Architecture:** Keep route handlers responsible for producing `PdfResult`, and centralize response-mode branching in a PDF-specific helper. `/render/html`, `/render/url`, and `/render/template` all call the same helper so binary, upload, JSON validation, and conflict behavior stay consistent.

**Tech Stack:** Kotlin/JVM 24, Ktor server routing and content negotiation, kotlinx.serialization, OpenHTMLToPDF, veraPDF, Gradle wrapper.

---

## File Structure

- Rename `src/main/kotlin/bambamboole/pdfua/http/ConvertRequest.kt` to `src/main/kotlin/bambamboole/pdfua/http/RenderHtmlRequest.kt`
  - Rename the DTO to `RenderHtmlRequest` while preserving JSON fields.
- Modify `src/main/kotlin/bambamboole/pdfua/http/controller/UploadResponse.kt`
  - Add `RenderPdfResponse`.
  - Add `respondPdfOrUpload`.
  - Add explicit JSON accept detection.
  - Keep `respondDocumentOrUpload` for image routes.
- Modify `src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt`
  - Add `POST /render/html`.
  - Replace PDF calls to `respondDocumentOrUpload` with `respondPdfOrUpload`.
- Delete `src/main/kotlin/bambamboole/pdfua/http/controller/Convert.kt`
  - The old `/convert` route is removed directly.
- Delete `src/main/kotlin/bambamboole/pdfua/http/controller/ConvertAndValidate.kt`
  - The old `/convert-and-validate` route is removed directly.
- Modify `src/main/kotlin/bambamboole/pdfua/Application.kt`
  - Remove imports and module calls for convert routes.
- Modify `src/main/resources/application.yaml`
  - Remove convert module entries.
- Modify `build.gradle.kts`
  - Patch OpenAPI binary/JSON response content for `/render/html`, `/render/url`, and `/render/template`.
- Modify docs:
  - `README.md`
  - `docs/content/docs/index.mdx`
  - `docs/content/docs/s3-upload.md`
  - Any remaining `/convert` or `/convert-and-validate` references found with `rg`.
- Modify tests:
  - Replace `/convert` calls with `/render/html`.
  - Remove or repurpose `/convert-and-validate` tests.
  - Add response negotiation tests.

---

### Task 1: Add Shared PDF Response Negotiation

**Files:**
- Modify: `src/main/kotlin/bambamboole/pdfua/http/controller/UploadResponse.kt`
- Test: `src/test/kotlin/bambamboole/pdfua/http/controller/PdfResponseNegotiationTest.kt`

- [ ] **Step 1: Write failing helper-level route tests**

Create `src/test/kotlin/bambamboole/pdfua/http/controller/PdfResponseNegotiationTest.kt`:

```kotlin
package bambamboole.pdfua.http.controller

import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.services.DocumentUploader
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun Application.pdfNegotiationTestModule(uploader: DocumentUploader? = null) {
    install(ContentNegotiation) { json() }
    routing {
        post("/test/pdf") {
            val result =
                PdfRenderer.convertHtmlToPdf(
                    html = """<!DOCTYPE html><html lang="en"><head><title>T</title><meta name="subject" content="T"/></head><body><h1>T</h1></body></html>""",
                )
            respondPdfOrUpload(result, uploader)
        }
    }
}

class PdfResponseNegotiationTest {
    private fun httpClient() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private fun permissiveUploader() = DocumentUploader(httpClient = httpClient(), timeoutMs = 5000, validateUrl = { _, _ -> })

    private class CapturingServer {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var contentType: String? = null
        var body: ByteArray = ByteArray(0)

        fun start() {
            server.createContext("/upload") { exchange ->
                contentType = exchange.requestHeaders.getFirst("Content-Type")
                body = exchange.requestBody.readBytes()
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            server.start()
        }

        val port: Int get() = server.address.port

        fun stop() = server.stop(0)
    }

    @Test
    fun missingAcceptReturnsBinaryPdf() =
        testApplication {
            application { pdfNegotiationTestModule() }

            val response = client.post("/test/pdf")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertNotNull(response.headers["X-Document-UUID"])
            assertTrue(response.readRawBytes().take(5).toByteArray().decodeToString().startsWith("%PDF-"))
        }

    @Test
    fun wildcardAcceptReturnsBinaryPdf() =
        testApplication {
            application { pdfNegotiationTestModule() }

            val response =
                client.post("/test/pdf") {
                    accept(ContentType.Any)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }

    @Test
    fun explicitJsonAcceptReturnsValidationAndBase64Pdf() =
        testApplication {
            application { pdfNegotiationTestModule() }

            val response =
                client.post("/test/pdf") {
                    accept(ContentType.Application.Json)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
            assertNotNull(response.headers["X-Document-UUID"])

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(body.containsKey("validation"))
            assertTrue(body.containsKey("pdf"))
            assertTrue(body["pdf"].toString().length > 100)
        }

    @Test
    fun explicitJsonAcceptWithUploadUrlReturns400() =
        testApplication {
            val target = CapturingServer().apply { start() }
            application { pdfNegotiationTestModule(permissiveUploader()) }
            try {
                val response =
                    client.post("/test/pdf") {
                        accept(ContentType.Application.Json)
                        header("X-Upload-Url", "http://127.0.0.1:${target.port}/upload")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("cannot be combined"))
                assertEquals(0, target.body.size)
            } finally {
                target.stop()
            }
        }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew test --tests '*PdfResponseNegotiationTest'
```

Expected: compilation fails because `respondPdfOrUpload` is not defined.

- [ ] **Step 3: Implement response DTO and helper**

Modify `src/main/kotlin/bambamboole/pdfua/http/controller/UploadResponse.kt` to add imports:

```kotlin
import bambamboole.pdfua.http.ValidationResponse
import bambamboole.pdfua.pdf.PdfResult
import bambamboole.pdfua.pdf.PdfValidator
import kotlinx.serialization.Serializable
import java.util.Base64
```

Add this DTO near `UPLOAD_URL_HEADER`:

```kotlin
@Serializable
data class RenderPdfResponse(
    val validation: ValidationResponse,
    val pdf: String,
)
```

Add this helper below `respondDocumentOrUpload`:

```kotlin
suspend fun RoutingContext.respondPdfOrUpload(
    result: PdfResult,
    uploader: DocumentUploader?,
) {
    val uploadUrl = call.request.header(UPLOAD_URL_HEADER)?.takeIf { it.isNotBlank() }
    val wantsJson = call.request.acceptItems().any { it.value.equals("application/json", ignoreCase = true) }

    if (wantsJson && uploadUrl != null) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Accept: application/json responses cannot be combined with X-Upload-Url"),
        )
        return
    }

    if (wantsJson) {
        call.response.header("X-Document-UUID", result.documentId)
        call.respond(
            HttpStatusCode.OK,
            RenderPdfResponse(
                validation = PdfValidator.validatePdf(result.bytes),
                pdf = Base64.getEncoder().encodeToString(result.bytes),
            ),
        )
        return
    }

    respondDocumentOrUpload(
        bytes = result.bytes,
        contentType = ContentType.Application.Pdf,
        fileName = "output.pdf",
        documentId = result.documentId,
        uploader = uploader,
    )
}
```

- [ ] **Step 4: Run helper tests to verify they pass**

Run:

```bash
./gradlew test --tests '*PdfResponseNegotiationTest'
```

Expected: all `PdfResponseNegotiationTest` tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/kotlin/bambamboole/pdfua/http/controller/UploadResponse.kt src/test/kotlin/bambamboole/pdfua/http/controller/PdfResponseNegotiationTest.kt
git commit -m "feat: add pdf response negotiation"
```

---

### Task 2: Add `/render/html`

**Files:**
- Rename: `src/main/kotlin/bambamboole/pdfua/http/ConvertRequest.kt` to `src/main/kotlin/bambamboole/pdfua/http/RenderHtmlRequest.kt`
- Modify: `src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt`
- Test: `src/test/kotlin/bambamboole/pdfua/http/controller/RenderHtmlRoutesTest.kt`

- [ ] **Step 1: Write failing `/render/html` route tests**

Create `src/test/kotlin/bambamboole/pdfua/http/controller/RenderHtmlRoutesTest.kt`:

```kotlin
package bambamboole.pdfua.http.controller

import bambamboole.pdfua.http.RenderHtmlRequest
import bambamboole.pdfua.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenderHtmlRoutesTest {
    private val validHtml =
        """<!DOCTYPE html><html lang="en"><head><title>Test</title><meta name="subject" content="Test"/></head><body><h1>Test</h1></body></html>"""

    @Test
    fun renderHtmlReturnsPdfByDefault() =
        testApplication {
            application { module() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertNotNull(response.headers["X-Document-UUID"])
            assertTrue(response.readRawBytes().take(5).toByteArray().decodeToString().startsWith("%PDF-"))
        }

    @Test
    fun renderHtmlReturnsJsonWhenExplicitlyAccepted() =
        testApplication {
            application { module() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["X-Document-UUID"])

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(body.containsKey("validation"))
            assertTrue(body.containsKey("pdf"))
            assertTrue(body["validation"].toString().contains("isCompliant"))

            val pdfBytes = Base64.getDecoder().decode(body["pdf"].toString().trim('"'))
            assertTrue(String(pdfBytes, 0, 5, Charsets.US_ASCII).startsWith("%PDF-"))
        }

    @Test
    fun renderHtmlRejectsEmptyHtml() =
        testApplication {
            application { module() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest("")))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew test --tests '*RenderHtmlRoutesTest'
```

Expected: compilation fails because `RenderHtmlRequest` is not defined, or the `/render/html` assertions fail with `404 Not Found`.

- [ ] **Step 3: Rename request DTO and file**

Run:

```bash
git mv src/main/kotlin/bambamboole/pdfua/http/ConvertRequest.kt src/main/kotlin/bambamboole/pdfua/http/RenderHtmlRequest.kt
```

Modify `src/main/kotlin/bambamboole/pdfua/http/RenderHtmlRequest.kt`:

```kotlin
package bambamboole.pdfua.http

import bambamboole.pdfua.template.FileAttachment
import kotlinx.serialization.Serializable

@Serializable
data class RenderHtmlRequest(
    val html: String,
    val baseUrl: String? = null,
    val attachments: List<FileAttachment>? = null,
)
```

- [ ] **Step 4: Add `/render/html` route**

Modify imports in `src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt`:

```kotlin
import bambamboole.pdfua.http.RenderHtmlRequest
```

Inside `renderRoutes`, add this route before `/render/template`:

```kotlin
    @KtorDescription(
        summary = "Render HTML to PDF",
        description = "Renders HTML to a PDF/A-3a compliant document with PDF/UA accessibility.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "PDF document or JSON validation response"),
            ResponseEntry("400", Nothing::class, description = "Invalid request or empty HTML"),
            ResponseEntry("500", Nothing::class, description = "Rendering failed"),
        ],
    )
    post("/render/html") {
        val request = call.receive<RenderHtmlRequest>()
        require(request.html.isNotBlank()) { "HTML content cannot be empty" }

        val baseUrl = request.baseUrl?.also { validateBaseUrl(it) } ?: ""

        val result =
            PdfRenderer.convertHtmlToPdf(
                html = request.html,
                producer = pdfProducer,
                assetResolver = assetResolver,
                baseUrl = baseUrl,
                attachments = request.attachments,
            )

        respondPdfOrUpload(result, uploader)
    }
```

- [ ] **Step 5: Run `/render/html` tests**

Run:

```bash
./gradlew test --tests '*RenderHtmlRoutesTest'
```

Expected: all `RenderHtmlRoutesTest` tests pass.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/kotlin/bambamboole/pdfua/http src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt src/test/kotlin/bambamboole/pdfua/http/controller/RenderHtmlRoutesTest.kt
git commit -m "feat: add render html endpoint"
```

---

### Task 3: Apply JSON Negotiation to Template and URL Rendering

**Files:**
- Modify: `src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt`
- Modify: `src/test/kotlin/bambamboole/pdfua/http/controller/RenderRoutesTest.kt`
- Modify: `src/test/kotlin/bambamboole/pdfua/http/controller/RenderUrlRoutesTest.kt`

- [ ] **Step 1: Add failing template JSON negotiation test**

Append to `RenderRoutesTest`:

```kotlin
    @Test
    fun rendersTemplateAsJsonWhenExplicitlyAccepted() =
        testApplication {
            application { module() }

            val body =
                """
                {"template":{"version":1,"rows":[
                  {"blocks":[{"type":"text","id":"intro","text":"Hello from a template"}]}
                ]}}
                """.trimIndent()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(body)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = kotlinx.serialization.json.Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json.containsKey("validation"))
            assertTrue(json.containsKey("pdf"))
        }
```

- [ ] **Step 2: Add failing URL JSON negotiation test**

Append to `RenderUrlRoutesTest`:

```kotlin
    @Test
    fun renderUrlReturnsJsonWhenExplicitlyAccepted() =
        testApplication {
            val target = CapturingHtmlServer(200, "text/html; charset=UTF-8", sampleHtml.toByteArray())
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val json = kotlinx.serialization.json.Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertTrue(json.containsKey("validation"))
                assertTrue(json.containsKey("pdf"))
            } finally {
                target.stop()
            }
        }
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew test --tests '*RenderRoutesTest.rendersTemplateAsJsonWhenExplicitlyAccepted' --tests '*RenderUrlRoutesTest.renderUrlReturnsJsonWhenExplicitlyAccepted'
```

Expected: tests fail because both endpoints return `application/pdf` bytes instead of JSON.

- [ ] **Step 4: Use `respondPdfOrUpload` in template route**

In `Render.kt`, replace the `/render/template` route's `respondDocumentOrUpload` block with:

```kotlin
        respondPdfOrUpload(result, uploader)
```

- [ ] **Step 5: Use `respondPdfOrUpload` in URL route helper**

In `Render.kt`, update `respondRenderedUrl` success branch:

```kotlin
            respondPdfOrUpload(pdfResult, uploader)
```

- [ ] **Step 6: Run focused render tests**

Run:

```bash
./gradlew test --tests '*RenderRoutesTest' --tests '*RenderUrlRoutesTest'
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/kotlin/bambamboole/pdfua/http/controller/Render.kt src/test/kotlin/bambamboole/pdfua/http/controller/RenderRoutesTest.kt src/test/kotlin/bambamboole/pdfua/http/controller/RenderUrlRoutesTest.kt
git commit -m "feat: support json validation for pdf render routes"
```

---

### Task 4: Remove Old Convert Routes and Update Existing Tests

**Files:**
- Delete: `src/main/kotlin/bambamboole/pdfua/http/controller/Convert.kt`
- Delete: `src/main/kotlin/bambamboole/pdfua/http/controller/ConvertAndValidate.kt`
- Delete: `src/test/kotlin/bambamboole/pdfua/http/controller/ConvertAndValidateRoutesTest.kt`
- Modify: `src/main/kotlin/bambamboole/pdfua/Application.kt`
- Modify: `src/main/resources/application.yaml`
- Modify: tests that currently post to `/convert`

- [ ] **Step 1: Remove old module wiring**

Modify `Application.kt`:

```kotlin
fun Application.module(jwkProvider: JwkProvider? = null) {
    bootstrap(jwkProvider)
    logging()
    serialization()
    statusPages()
    cors()
    rateLimit()
    auth()
    swagger()
    health()
    templateSchema()
    render()
    validation()
    renderImage()
    identify()
}
```

Remove these imports:

```kotlin
import bambamboole.pdfua.http.controller.convert
import bambamboole.pdfua.http.controller.convertAndValidate
```

- [ ] **Step 2: Remove old YAML modules**

In `src/main/resources/application.yaml`, remove:

```yaml
      - bambamboole.pdfua.http.controller.ConvertKt.convert
      - bambamboole.pdfua.http.controller.ConvertAndValidateKt.convertAndValidate
```

- [ ] **Step 3: Delete old route files**

Run:

```bash
git rm src/main/kotlin/bambamboole/pdfua/http/controller/Convert.kt
git rm src/main/kotlin/bambamboole/pdfua/http/controller/ConvertAndValidate.kt
git rm src/test/kotlin/bambamboole/pdfua/http/controller/ConvertAndValidateRoutesTest.kt
```

- [ ] **Step 4: Replace imports and endpoint strings in tests**

Run this search:

```bash
rg -n 'ConvertRequest|"/convert"|convert-and-validate|convertRoutes|convertModule' src/test src/main
```

For each remaining test import:

```kotlin
import bambamboole.pdfua.http.ConvertRequest
```

replace with:

```kotlin
import bambamboole.pdfua.http.RenderHtmlRequest
```

For each request serialization:

```kotlin
Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(html))
```

replace with:

```kotlin
Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(html))
```

For endpoint calls:

```kotlin
client.post("/convert")
```

replace with:

```kotlin
client.post("/render/html")
```

For upload helper tests in `UploadRoutesTest`, replace:

```kotlin
private fun Application.convertModule(uploader: DocumentUploader?) {
    install(ContentNegotiation) { json() }
    routing { convertRoutes(uploader = uploader) }
}
```

with:

```kotlin
private fun Application.renderPdfModule(uploader: DocumentUploader?) {
    install(ContentNegotiation) { json() }
    routing { renderRoutes(uploader = uploader) }
}
```

Then replace `application { convertModule(...) }` with `application { renderPdfModule(...) }`.

- [ ] **Step 5: Rename `ConvertRoutesTest`**

Run:

```bash
git mv src/test/kotlin/bambamboole/pdfua/http/controller/ConvertRoutesTest.kt src/test/kotlin/bambamboole/pdfua/http/controller/RenderHtmlConversionRoutesTest.kt
```

Inside the file, rename:

```kotlin
class ConvertRoutesTest
```

to:

```kotlin
class RenderHtmlConversionRoutesTest
```

Update comments that say `/convert` to say `/render/html`.

- [ ] **Step 6: Run compile-focused tests**

Run:

```bash
./gradlew test --tests '*RenderHtml*' --tests '*UploadRoutesTest' --tests '*ApplicationConfigTest' --tests '*JwtAuthTest'
```

Expected: compilation succeeds and selected tests pass.

- [ ] **Step 7: Confirm removed endpoints**

Add these tests to `RenderHtmlRoutesTest`:

```kotlin
    @Test
    fun convertIsNotRegistered() =
        testApplication {
            application { module() }

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
```

```kotlin
    @Test
    fun convertAndValidateIsNotRegistered() =
        testApplication {
            application { module() }

            val response =
                client.post("/convert-and-validate") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
```

Run:

```bash
./gradlew test --tests '*RenderHtmlRoutesTest'
```

Expected: all `RenderHtmlRoutesTest` tests pass.

- [ ] **Step 8: Commit**

Run:

```bash
git add src/main/kotlin src/test/kotlin src/main/resources/application.yaml
git commit -m "refactor: remove legacy convert routes"
```

---

### Task 5: Update OpenAPI and Documentation

**Files:**
- Modify: `build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/content/docs/index.mdx`
- Modify: `docs/content/docs/s3-upload.md`
- Modify: `docs/content/docs/HTML/url-to-pdf.mdx`
- Modify: `docs/openapi/openapi.json` when regenerated by build

- [ ] **Step 1: Update OpenAPI patch responses**

In `build.gradle.kts`, replace the existing `binarySchema` and `setBinaryResponse` helper block:

```kotlin
        fun binarySchema() = mapOf("type" to "string", "format" to "binary")

        fun setBinaryResponse(
            path: String,
            method: String,
            contentTypes: List<String>,
        ) {
            @Suppress("UNCHECKED_CAST")
            val response =
                (paths[path]?.get(method)?.get("responses") as? MutableMap<String, Any?>)
                    ?.get("200") as? MutableMap<String, Any?> ?: return
            response["content"] = contentTypes.associateWith { mapOf("schema" to binarySchema()) }
        }
```

with:

```kotlin
        fun binarySchema() = mapOf("type" to "string", "format" to "binary")

        fun renderPdfJsonSchema() =
            mapOf(
                "type" to "object",
                "required" to listOf("validation", "pdf"),
                "properties" to
                    mapOf(
                        "validation" to mapOf("\$ref" to "#/components/schemas/ValidationResponse"),
                        "pdf" to mapOf("type" to "string", "format" to "byte"),
                    ),
            )

        fun setResponseContent(
            path: String,
            method: String,
            content: Map<String, Map<String, Any>>,
        ) {
            @Suppress("UNCHECKED_CAST")
            val response =
                (paths[path]?.get(method)?.get("responses") as? MutableMap<String, Any?>)
                    ?.get("200") as? MutableMap<String, Any?> ?: return
            response["content"] = content
        }

        fun setBinaryResponse(
            path: String,
            method: String,
            contentTypes: List<String>,
        ) {
            setResponseContent(
                path = path,
                method = method,
                content = contentTypes.associateWith { mapOf("schema" to binarySchema()) },
            )
        }

        fun setPdfRenderResponse(path: String) {
            setResponseContent(
                path = path,
                method = "post",
                content =
                    mapOf(
                        "application/pdf" to mapOf("schema" to binarySchema()),
                        "application/json" to mapOf("schema" to renderPdfJsonSchema()),
                    ),
            )
        }
```

Then replace the old response patch calls:

```kotlin
        setBinaryResponse("/convert", "post", listOf("application/pdf"))
        setBinaryResponse("/render/template", "post", listOf("application/pdf"))
        setBinaryResponse("/render", "post", listOf("image/png", "image/jpeg"))
```

with:

```kotlin
        setPdfRenderResponse("/render/html")
        setPdfRenderResponse("/render/url")
        setPdfRenderResponse("/render/template")
        setBinaryResponse("/render", "post", listOf("image/png", "image/jpeg"))
```

- [ ] **Step 2: Update README endpoint table**

Replace the old endpoint rows in `README.md` with:

```markdown
| `POST /render/html`      | HTML -> PDF/A-3a with PDF/UA accessibility            |
| `POST /render/url`       | Public URL -> PDF/A-3a with PDF/UA accessibility      |
| `POST /render/template`  | JSON template -> PDF/A-3a with PDF/UA accessibility   |
| `POST /validate`         | Validate a PDF with veraPDF                           |
| `POST /render`           | HTML -> PNG/JPEG image                                |
| `POST /identify`         | Identify PDF metadata and embedded document UUID      |
| `GET  /schema`           | JSON Schema (Draft 2020-12) for `/render/template`    |
```

Add a short response negotiation paragraph:

```markdown
PDF render endpoints return binary `application/pdf` by default. Send `Accept: application/json` to receive `{ "validation": ..., "pdf": "base64" }`. JSON response mode cannot be combined with `X-Upload-Url`.
```

- [ ] **Step 3: Update docs references**

Run:

```bash
rg -n '/convert|convert-and-validate|/render/html|X-Upload-Url' README.md docs build.gradle.kts src/main src/test
```

Update remaining docs references so upload-capable endpoints list:

```text
POST /render/html, POST /render/url, POST /render/template, POST /render
```

Remove references to `/convert-and-validate`.

- [ ] **Step 4: Regenerate OpenAPI**

Run:

```bash
./gradlew classes
```

Expected: Gradle completes successfully and updates `docs/openapi/openapi.json` if generated output changes.

- [ ] **Step 5: Commit**

Run:

```bash
git add build.gradle.kts README.md docs
git commit -m "docs: document render html negotiation"
```

---

### Task 6: Final Verification and Cleanup

**Files:**
- Modify only files required by failures from verification.

- [ ] **Step 1: Search for stale references**

Run:

```bash
rg -n 'ConvertRequest|ConvertAndValidate|convertAndValidate|convertRoutes|"/convert"|/convert-and-validate' src README.md docs build.gradle.kts
```

Expected: no results, except historical text inside the committed design or implementation plan under `docs/superpowers`.

- [ ] **Step 2: Run focused route tests**

Run:

```bash
./gradlew test --tests '*Render*RoutesTest' --tests '*RenderHtml*' --tests '*UploadRoutesTest' --tests '*ValidationRoutesTest' --tests '*IdentifyRoutesTest'
```

Expected: all selected tests pass.

- [ ] **Step 3: Run quality checks**

Run:

```bash
./gradlew spotlessCheck detekt --no-daemon
```

Expected: both checks pass.

- [ ] **Step 4: Run full test suite**

Run:

```bash
./gradlew test --no-daemon
```

Expected: full test suite passes.

- [ ] **Step 5: Inspect generated PDF fixture changes**

Run:

```bash
git status --short
```

Expected: no unexpected fixture baseline changes. If `generated.pdf` files appear from existing visual tests, inspect them and remove them from the worktree unless they are intentionally tracked fixture updates.

- [ ] **Step 6: Final commit for verification-only fixes**

If verification required fixes after Task 5, run:

```bash
git add src README.md docs build.gradle.kts
git commit -m "fix: complete render endpoint migration"
```

If no fixes were required, skip this commit.
