package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.html.TemplateRenderer
import bambamboole.pdfua.http.RenderHtmlRequest
import bambamboole.pdfua.http.ValidationErrorResponse
import bambamboole.pdfua.pdf.PdfRenderOptions
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
import bambamboole.pdfua.services.FetchResult
import bambamboole.pdfua.services.HtmlSourceFetcher
import bambamboole.pdfua.services.RenderOptions
import bambamboole.pdfua.template.FileAttachment
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.ValidationCodes
import bambamboole.pdfua.template.ValidationIssue
import bambamboole.pdfua.template.serializationIssue
import bambamboole.pdfua.template.validate
import com.openhtmltopdf.extend.FSStreamFactory
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
    val options: RenderOptions = RenderOptions(),
)

@Serializable
data class RenderUrlRequest(
    val url: String,
    val attachments: List<FileAttachment>? = null,
    val embedColorProfile: Boolean = true,
)

private const val SERIALIZATION_CAUSE_UNWRAP_DEPTH = 4

private fun Throwable.unwrapToSerializationException(): SerializationException? {
    var current: Throwable? = this
    repeat(SERIALIZATION_CAUSE_UNWRAP_DEPTH) {
        val candidate = current
        if (candidate is SerializationException) return candidate
        current = candidate?.cause
    }
    return null
}

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.renderRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null,
    uploader: DocumentUploader? = null,
    htmlSourceFetcher: HtmlSourceFetcher? = null,
) {
    @KtorDescription(
        summary = "Render HTML to PDF",
        description = "Renders HTML to a PDF/A-3a + PDF/UA document.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "PDF document"),
            ResponseEntry(
                "200",
                RenderPdfResponse::class,
                description = "Validation result with base64 PDF when Accept is application/json",
            ),
            ResponseEntry("400", Nothing::class, description = "Invalid request or empty HTML"),
            ResponseEntry("500", Nothing::class, description = "Rendering failed"),
        ],
    )
    post("/render/html") {
        renderHtml(pdfProducer, assetResolver, uploader)
    }

    @KtorDescription(
        summary = "Render a template to PDF",
        description = "Renders a JSON template to a PDF/A-3a document or JSON validation response.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "PDF document"),
            ResponseEntry(
                "200",
                RenderPdfResponse::class,
                description = "Validation result with base64 PDF when Accept is application/json",
            ),
            ResponseEntry("400", ValidationErrorResponse::class, description = "Invalid template, data, or request"),
            ResponseEntry("500", Nothing::class, description = "Rendering failed"),
        ],
    )
    post("/render/template") {
        renderTemplate(pdfProducer, assetResolver, uploader)
    }

    @KtorDescription(
        summary = "Render a URL to PDF",
        description = "Fetches the HTML at the given URL and renders it to a PDF or JSON validation response.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "PDF document"),
            ResponseEntry(
                "200",
                RenderPdfResponse::class,
                description = "Validation result with base64 PDF when Accept is application/json",
            ),
            ResponseEntry("400", Nothing::class, description = "Invalid URL or fetch failure"),
            ResponseEntry("500", Nothing::class, description = "Rendering failed"),
        ],
    )
    post("/render/url") {
        renderUrl(htmlSourceFetcher, pdfProducer, assetResolver, uploader)
    }
}

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
    val issues = request.template.validate(request.data)
    if (issues.isNotEmpty()) {
        call.respond(HttpStatusCode.BadRequest, ValidationErrorResponse(issues = issues))
        return
    }

    request.options.baseUrl
        .takeIf { it.isNotEmpty() }
        ?.let { validateBaseUrl(it) }
    val html = TemplateRenderer.render(request.template, request.data, request.options)

    val result =
        PdfRenderer.convertHtmlToPdf(
            html = html,
            producer = pdfProducer,
            assetResolver = assetResolver,
            baseUrl = request.options.baseUrl,
            attachments = request.template.attachments,
            options = PdfRenderOptions(embedColorProfile = request.options.embedColorProfile),
        )

    respondPdfOrUpload(result, uploader)
}

@Suppress("TooGenericExceptionCaught")
private suspend fun RoutingContext.receiveRenderRequest(): RenderRequest? =
    try {
        call.receive<RenderRequest>()
    } catch (e: Exception) {
        val serializationCause = e.unwrapToSerializationException()
        val issue =
            serializationCause?.let(::serializationIssue)
                ?: ValidationIssue("$", ValidationCodes.INVALID_JSON, e.message ?: "Invalid request body")
        call.respond(HttpStatusCode.BadRequest, ValidationErrorResponse(issues = listOf(issue)))
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
                mapOf("error" to "URL rendering is not configured"),
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
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
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
