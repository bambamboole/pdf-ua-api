package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.image.ImageRenderer
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

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.renderImageRoutes(
    assetResolver: FSStreamFactory? = null,
    uploader: DocumentUploader? = null,
) {
    @KtorDescription(
        summary = "Render HTML to image",
        description = "Renders HTML to a PNG or JPEG image. Returns the image binary.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ByteArray::class, description = "Rendered image"),
            ResponseEntry("400", Nothing::class, description = "Invalid request"),
            ResponseEntry("500", Nothing::class, description = "Rendering failed"),
        ],
    )
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
    }
}

private const val MAX_RENDER_WIDTH = 4096
