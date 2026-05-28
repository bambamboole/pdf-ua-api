package bambamboole.pdfua.models

import bambamboole.pdfua.models.template.Template
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RenderOptions(
    val title: String = "Document",
    val baseUrl: String = "",
)

@Serializable
data class RenderRequest(
    val template: Template,
    /** Per-block content overrides, keyed by block id. Object for most blocks; array of row objects for tables. */
    val data: Map<String, JsonElement> = emptyMap(),
    val options: RenderOptions = RenderOptions(),
)
