package bambamboole.pdf.api.models

import bambamboole.pdf.api.models.template.Template
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RenderOptions(
    val title: String = "Document",
    val baseUrl: String = "",
)

@Serializable
data class RenderRequest(
    val template: Template,
    /** Per-block content overrides, keyed by block id. */
    val data: Map<String, JsonObject> = emptyMap(),
    val options: RenderOptions = RenderOptions(),
)
