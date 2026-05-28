package bambamboole.pdfua.routes

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val TemplateBuilderResourceRoot = "webui/template-builder"

fun Route.templateBuilderWebRoutes() {
    get("/template-builder") {
        val indexHtml = requireNotNull(
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("$TemplateBuilderResourceRoot/index.html"),
        ) {
            "Template builder web UI resources are missing. Run the frontend build before starting the API."
        }.bufferedReader().use { it.readText() }

        call.respondText(indexHtml, ContentType.Text.Html)
    }

    staticResources("/template-builder", TemplateBuilderResourceRoot)
}
