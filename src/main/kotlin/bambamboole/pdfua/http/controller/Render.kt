package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.http.ErrorResponse
import bambamboole.pdfua.http.RenderHtmlRequest
import bambamboole.pdfua.http.TEMPLATE_SCHEMA_REF
import bambamboole.pdfua.http.binarySchema
import bambamboole.pdfua.pdf.PdfRenderOptions
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
import bambamboole.pdfua.services.FetchResult
import bambamboole.pdfua.services.HtmlSourceFetcher
import bambamboole.pdfua.services.TemplatePdfRenderResult
import bambamboole.pdfua.services.TemplatePdfRenderService
import bambamboole.pdfua.template.FileAttachment
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.toJsonValidationIssue
import com.openhtmltopdf.extend.FSStreamFactory
import io.ktor.http.*
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.net.URI

fun Application.render() {
    val config: AppConfig by dependencies
    val assetResolver: AssetResolver by dependencies
    val uploader: DocumentUploader? by dependencies
    val htmlSourceFetcher: HtmlSourceFetcher by dependencies
    routing {
        expensiveRoute(config) {
            renderRoutes(config.pdfProducer, assetResolver, uploader, htmlSourceFetcher)
        }
    }
}

@Serializable
data class RenderRequest(
    val template: Template,
    /** Per-block content overrides, keyed by block id. Object for most blocks; array of row objects for tables. */
    val data: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class RenderUrlRequest(
    val url: String,
    val attachments: List<FileAttachment>? = null,
    val embedColorProfile: Boolean = true,
)

@OptIn(ExperimentalKtorApi::class)
fun Route.renderRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null,
    uploader: DocumentUploader? = null,
    htmlSourceFetcher: HtmlSourceFetcher? = null,
) {
    post("/render/html") {
        renderHtml(pdfProducer, assetResolver, uploader)
    }.describe {
        pdfRenderOperation(
            summary = "Render HTML to PDF",
            operationDescription =
                "Renders HTML to a PDF/A-3a + PDF/UA document, or a JSON validation response when " +
                    "Accept is application/json.",
            badRequestDescription = "Invalid request, empty HTML, or JSON/upload conflict",
        ) {
            requestBody {
                required = true
                schema = jsonSchema<RenderHtmlRequest>()
            }
        }
    }

    post("/render/template") {
        renderTemplate(pdfProducer, assetResolver, uploader)
    }.describe {
        pdfRenderOperation(
            summary = "Render a template to PDF",
            operationDescription =
                "Renders a JSON template to a PDF/A-3a document, or a JSON validation response when " +
                    "Accept is application/json.",
            badRequestDescription = "Invalid template, data, request, or JSON/upload conflict",
        ) {
            requestBody {
                required = true
                schema = renderRequestSchema()
            }
        }
    }

    post("/render/url") {
        renderUrl(htmlSourceFetcher, pdfProducer, assetResolver, uploader)
    }.describe {
        pdfRenderOperation(
            summary = "Render a URL to PDF",
            operationDescription =
                "Fetches the HTML at the given URL and renders it to a PDF, or a JSON validation " +
                    "response when Accept is application/json.",
            badRequestDescription = "Invalid URL, fetch failure, or JSON/upload conflict",
        ) {
            requestBody {
                required = true
                schema = jsonSchema<RenderUrlRequest>()
            }
        }
    }
}

/**
 * Shared OpenAPI metadata for the PDF render routes: the `X-Upload-Url` header, the per-route
 * request body, and the negotiated 200 (PDF or JSON) plus the upload (204/502) responses.
 */
@OptIn(ExperimentalKtorApi::class)
private fun Operation.Builder.pdfRenderOperation(
    summary: String,
    operationDescription: String,
    badRequestDescription: String,
    configureRequestBody: Operation.Builder.() -> Unit,
) {
    tag("Rendering")
    this.summary = summary
    this.description = operationDescription
    uploadUrlHeaderParameter()
    configureRequestBody()
    val pdfJsonSchema = jsonSchema<RenderPdfResponse>()
    responses {
        HttpStatusCode.OK {
            description = "PDF document or JSON validation response"
            ContentType.Application.Pdf { schema = binarySchema() }
            ContentType.Application.Json { schema = pdfJsonSchema }
        }
        HttpStatusCode.BadRequest { description = badRequestDescription }
        HttpStatusCode.NoContent { description = "PDF uploaded successfully" }
        HttpStatusCode.BadGateway { description = "Upload target rejected the request or was unreachable" }
        HttpStatusCode.InternalServerError { description = "Rendering failed" }
    }
}

/**
 * Hand-authored schema for [RenderRequest]; the `template` field references the shared `Template`
 * component (`TEMPLATE_SCHEMA_REF`), which is the canonical `TemplateJsonSchema` injected into the
 * spec's components.
 */
private fun renderRequestSchema(): JsonSchema =
    JsonSchema(
        type = JsonType.OBJECT,
        properties =
            mapOf(
                "data" to
                    ReferenceOr.Value(
                        JsonSchema(
                            type = JsonType.OBJECT,
                            description =
                                "Per-block content overrides, keyed by block id. Object for most blocks; " +
                                    "array of row objects for tables.",
                        ),
                    ),
                "template" to ReferenceOr.Reference(TEMPLATE_SCHEMA_REF),
            ),
        required = listOf("template"),
    )

private suspend fun RoutingContext.renderHtml(
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
    uploader: DocumentUploader?,
) {
    if (rejectPdfJsonUploadConflict()) return

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
            options = PdfRenderOptions(embedColorProfile = request.embedColorProfile),
        )

    respondPdfOrUpload(result, uploader)
}

internal fun validateBaseUrl(baseUrl: String) {
    val uri = URI.create(baseUrl)
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "baseUrl must use http or https scheme, got: $scheme"
    }
}

private suspend fun RoutingContext.renderTemplate(
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
    uploader: DocumentUploader?,
) {
    if (rejectPdfJsonUploadConflict()) return

    val request = receiveRenderRequest() ?: return
    when (val result = TemplatePdfRenderService(pdfProducer, assetResolver).render(request.template, request.data)) {
        is TemplatePdfRenderResult.Success -> {
            respondPdfOrUpload(result.pdf, uploader)
        }

        is TemplatePdfRenderResult.ValidationFailed -> {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "validation_failed", issues = result.issues))
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun RoutingContext.receiveRenderRequest(): RenderRequest? =
    try {
        call.receive<RenderRequest>()
    } catch (e: Exception) {
        val issue = e.toJsonValidationIssue("Invalid request body")
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "validation_failed", issues = listOf(issue)))
        null
    }

private suspend fun RoutingContext.renderUrl(
    htmlSourceFetcher: HtmlSourceFetcher?,
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
    uploader: DocumentUploader?,
) {
    if (rejectPdfJsonUploadConflict()) return

    val fetcher =
        htmlSourceFetcher ?: run {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("URL rendering is not configured"),
            )
            return
        }
    val request = call.receive<RenderUrlRequest>()
    require(request.url.isNotBlank()) { "url cannot be empty" }
    respondRenderedUrl(
        result = fetcher.fetch(request.url),
        attachments = request.attachments,
        embedColorProfile = request.embedColorProfile,
        pdfProducer = pdfProducer,
        assetResolver = assetResolver,
        uploader = uploader,
    )
}

private suspend fun RoutingContext.respondRenderedUrl(
    result: FetchResult,
    attachments: List<FileAttachment>?,
    embedColorProfile: Boolean,
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
    uploader: DocumentUploader?,
) {
    when (result) {
        is FetchResult.Failure -> {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        }

        is FetchResult.Success -> {
            val pdfResult =
                PdfRenderer.convertHtmlToPdf(
                    html = result.html,
                    producer = pdfProducer,
                    assetResolver = assetResolver,
                    baseUrl = result.finalUrl,
                    attachments = attachments,
                    options = PdfRenderOptions(embedColorProfile = embedColorProfile),
                )
            respondPdfOrUpload(pdfResult, uploader)
        }
    }
}
