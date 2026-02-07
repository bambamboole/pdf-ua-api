package bambamboole.pdf.api.routes

import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Routes for the web UI
 */
fun Route.indexRoutes() {
    get("/") {
        call.respond(MustacheContent("index.mustache", mapOf(
            "templates" to loadExampleTemplates()
        )))
    }
}

@Serializable
data class Template(
    val name: String,
    val html: String
)

/**
 * Load all example HTML files from resources
 */
private fun loadExampleTemplates(): List<Template> {
    val examplesPath = "examples"
    val resourceUrl = object {}.javaClass.classLoader.getResource(examplesPath)
        ?: return emptyList()

    val examplesDir = File(resourceUrl.toURI())
    if (!examplesDir.exists() || !examplesDir.isDirectory) {
        return emptyList()
    }

    return examplesDir.listFiles()
        ?.filter { it.isFile && it.extension == "html" }
        ?.map { htmlFile ->
            Template(
                name = formatExampleName(htmlFile.nameWithoutExtension),
                html = htmlFile.readText()
            )
        }
        ?.sortedBy { it.name }
        ?: emptyList()
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
