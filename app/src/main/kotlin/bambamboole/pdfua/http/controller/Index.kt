package bambamboole.pdfua.http.controller

import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.indexRoutes() {
    get("/") {
        call.respond(
            MustacheContent(
                "index.mustache",
                mapOf("templates" to loadExampleTemplates()),
            ),
        )
    }
}

@Serializable
private data class ExampleTemplate(
    val name: String,
    val html: String,
)

private fun loadExampleTemplates(): List<ExampleTemplate> {
    val exampleFiles = listOf(
        "simple-document.html",
        "invoice-example-1.html",
        "styled-table.html",
        "table-pagination.html",
        "font-variations.html",
        "custom-creator.html",
    )

    return exampleFiles.mapNotNull { fileName ->
        try {
            val resourcePath = "examples/$fileName"
            val content = object {}.javaClass.classLoader
                .getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }

            if (content != null) {
                ExampleTemplate(
                    name = formatExampleName(fileName.removeSuffix(".html")),
                    html = content,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.name }
}

private fun formatExampleName(fileName: String): String =
    fileName.split("-").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
