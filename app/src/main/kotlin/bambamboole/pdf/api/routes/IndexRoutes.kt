package bambamboole.pdf.api.routes

import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Routes for the web UI
 */
fun Route.indexRoutes() {
    get("/") {
        call.respond(
            MustacheContent(
                "index.mustache", mapOf(
                    "templates" to loadExampleTemplates()
                )
            )
        )
    }
}

@Serializable
data class Template(
    val name: String,
    val html: String
)

/**
 * Load all example HTML files from resources
 * Works both in development (filesystem) and production (JAR)
 */
private fun loadExampleTemplates(): List<Template> {
    val exampleFiles = listOf(
        "simple-document.html",
        "invoice-example-1.html",
        "styled-table.html",
        "table-pagination.html",
        "font-variations.html",
        "custom-creator.html"
    )

    return exampleFiles.mapNotNull { fileName ->
        try {
            val resourcePath = "examples/$fileName"
            val content = object {}.javaClass.classLoader
                .getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }

            if (content != null) {
                Template(
                    name = formatExampleName(fileName.removeSuffix(".html")),
                    html = content
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.name }
}

/**
 * Convert example file name to display name
 * e.g., "simple-document" -> "Simple Document"
 */
private fun formatExampleName(fileName: String): String {
    return fileName
        .split("-")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}
