package org.example.app.models

import kotlinx.serialization.Serializable

@Serializable
data class ConvertRequest(val html: String)
