package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.http.binarySchema
import bambamboole.pdfua.image.ImageRenderer
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
import com.openhtmltopdf.extend.FSStreamFactory
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable

@Serializable
data class RenderImageRequest(
    val html: String,
    val baseUrl: String? = null,
    val format: String = "png",
    val width: Int = 800,
)

fun Application.renderImage() {
    val config: AppConfig by dependencies
    val assetResolver: AssetResolver by dependencies
    val uploader: DocumentUploader? by dependencies
    routing {
        expensiveRoute(config) {
            renderImageRoutes(assetResolver, uploader)
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.renderImageRoutes(
    assetResolver: FSStreamFactory? = null,
    uploader: DocumentUploader? = null,
) {
    post("/render") {
        val request = call.receive<RenderImageRequest>()
        require(request.html.isNotBlank()) { "HTML content cannot be empty" }

        val format = request.format.lowercase()
        require(format in listOf("png", "jpg")) {
            "Unsupported format '$format'. Supported formats: png, jpg"
        }
        require(request.width in 1..MAX_RENDER_WIDTH) { "Width must be between 1 and $MAX_RENDER_WIDTH" }

        val baseUrl = request.baseUrl?.also { validateBaseUrl(it) } ?: ""

        val imageBytes =
            ImageRenderer.renderHtmlToImage(
                html = request.html,
                format = format,
                width = request.width,
                assetResolver = assetResolver,
                baseUrl = baseUrl,
            )

        val contentType = if (format == "jpg") ContentType.Image.JPEG else ContentType.Image.PNG
        val extension = if (format == "jpg") "jpg" else "png"

        respondDocumentOrUpload(
            bytes = imageBytes,
            contentType = contentType,
            fileName = "output.$extension",
            documentId = null,
            uploader = uploader,
        )
    }.describe {
        tag("Rendering")
        summary = "Render HTML to image"
        description = "Renders HTML to a PNG or JPEG image, or uploads it when X-Upload-Url is provided."
        uploadUrlHeaderParameter()
        requestBody {
            required = true
            schema = jsonSchema<RenderImageRequest>()
        }
        responses {
            HttpStatusCode.OK {
                description = "Rendered image"
                ContentType.Image.PNG { schema = binarySchema() }
                ContentType.Image.JPEG { schema = binarySchema() }
            }
            HttpStatusCode.BadRequest { description = "Invalid request or upload URL" }
            HttpStatusCode.NoContent { description = "Image uploaded successfully" }
            HttpStatusCode.BadGateway { description = "Upload target rejected the request or was unreachable" }
            HttpStatusCode.InternalServerError { description = "Rendering failed" }
        }
    }
}

private const val MAX_RENDER_WIDTH = 4096
