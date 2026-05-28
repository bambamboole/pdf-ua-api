package bambamboole.pdfua.routes

import io.ktor.http.ContentType
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val TEMPLATE_BUILDER_RESOURCE_ROOT = "webui/template-builder"

fun Route.templateBuilderWebRoutes() {
    get("/template-builder") {
        val indexHtml =
            requireNotNull(
                Thread
                    .currentThread()
                    .contextClassLoader
                    .getResourceAsStream("$TEMPLATE_BUILDER_RESOURCE_ROOT/index.html"),
            ) {
                "Template builder web UI resources are missing. Run the frontend build before starting the API."
            }.bufferedReader().use { it.readText() }

        call.respondText(indexHtml, ContentType.Text.Html)
    }

    staticResources("/template-builder", TEMPLATE_BUILDER_RESOURCE_ROOT)
}
