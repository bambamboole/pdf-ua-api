package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.http.ConvertRequest
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
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
import java.net.URI

fun Application.convert() {
    val config: AppConfig by dependencies
    val assetResolver: AssetResolver by dependencies
    val uploader: DocumentUploader? by dependencies
    routing {
        expensiveRoute(config) {
            convertRoutes(config.pdfProducer, assetResolver, uploader)
        }
    }
}

@GenerateOpenApi
@Tag(["Conversion"])
fun Route.convertRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null,
    uploader: DocumentUploader? = null,
) {
    @KtorDescription(
        summary = "Convert HTML to PDF",
        description = "Converts HTML to a PDF/A-3a compliant document with PDF/UA accessibility. Returns the PDF binary.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "PDF document"),
            ResponseEntry("400", Nothing::class, description = "Invalid request or empty HTML"),
            ResponseEntry("500", Nothing::class, description = "Conversion failed"),
        ],
    )
    post("/convert") {
        val request = call.receive<ConvertRequest>()
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

        respondDocumentOrUpload(
            bytes = result.bytes,
            contentType = ContentType.Application.Pdf,
            fileName = "output.pdf",
            documentId = result.documentId,
            uploader = uploader,
        )
    }
}

internal fun validateBaseUrl(baseUrl: String) {
    val uri = URI.create(baseUrl)
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "baseUrl must use http or https scheme, got: $scheme"
    }
}
