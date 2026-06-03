package bambamboole.pdfua.template

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CodeBlockTest {
    private val json = Json

    @Test
    fun rawContentPayloadAndDescribe() {
        val content = RawContent(value = "ABC-123")
        assertEquals("ABC-123", content.toPayload())
        assertEquals("ABC-123", content.describe())
    }

    @Test
    fun urlContentDecodesByTypeDiscriminator() {
        val content =
            json.decodeFromString(
                CodeContent.serializer(),
                """{"type":"url","url":"https://example.com"}""",
            )
        val url = assertIs<UrlContent>(content)
        assertEquals("https://example.com", url.toPayload())
    }
}
