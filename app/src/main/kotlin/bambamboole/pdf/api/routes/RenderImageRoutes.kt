package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.RenderImageRequest
import bambamboole.pdf.api.services.ImageRenderService
import com.openhtmltopdf.extend.FSStreamFactory
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.renderImageRoutes(assetResolver: FSStreamFactory? = null) {
    @KtorDescription(
        summary = "Render HTML to image",
        description = "Renders HTML to a PNG or JPEG image. Returns the image binary."
    )
    @KtorResponds([
        ResponseEntry("200", ByteArray::class, description = "Rendered image"),
        ResponseEntry("400", Nothing::class, description = "Invalid request"),
        ResponseEntry("500", Nothing::class, description = "Rendering failed")
    ])
    post("/render") {
        try {
            val request = call.receive<RenderImageRequest>()

            if (request.html.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "HTML content cannot be empty")
                )
                return@post
            }

            val format = request.format.lowercase()
            if (format !in listOf("png", "jpg")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Unsupported format '$format'. Supported formats: png, jpg")
                )
                return@post
            }

            if (request.width !in 1..4096) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Width must be between 1 and 4096")
                )
                return@post
            }

            val baseUrl = request.baseUrl?.also { validateBaseUrl(it) } ?: ""

            val imageBytes = ImageRenderService.renderHtmlToImage(
                html = request.html,
                format = format,
                width = request.width,
                assetResolver = assetResolver,
                baseUrl = baseUrl
            )

            val contentType = if (format == "jpg") ContentType.Image.JPEG else ContentType.Image.PNG
            val extension = if (format == "jpg") "jpg" else "png"

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "output.$extension"
                ).toString()
            )

            call.respondBytes(
                bytes = imageBytes,
                contentType = contentType,
                status = HttpStatusCode.OK
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (e.message ?: "Invalid request"))
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to render HTML to image: ${e.message}")
            )
        }
    }
}
