package bambamboole.pdfua.mcp

import bambamboole.pdfua.services.FetchResult
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.mcpClient
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMcpApi::class)
class PdfUaMcpServerTest {
    @Test
    fun exposesRenderTools() =
        runBlocking {
            withMcpClient { client ->
                val tools = client.listTools().tools.map { it.name }

                assertTrue(tools.containsAll(listOf("render_template", "render_html", "render_url")))
            }
        }

    @Test
    fun renderTemplateToolReturnsBase64Pdf() =
        runBlocking {
            withMcpClient { client ->
                val result = client.callTool(toolCall("render_template", templateArguments()))

                assertPdfResult(result)
            }
        }

    @Test
    fun renderTemplateToolReturnsValidationError() =
        runBlocking {
            withMcpClient { client ->
                val result =
                    client.callTool(
                        toolCall(
                            "render_template",
                            buildJsonObject {
                                putJsonObject("template") {
                                    put("version", 1)
                                }
                            },
                        ),
                    )

                assertEquals(true, result.isError)
                val structured = assertNotNull(result.structuredContent)
                assertEquals("validation_failed", structured["error"]?.jsonPrimitive?.content)
            }
        }

    @Test
    fun renderHtmlToolReturnsBase64Pdf() =
        runBlocking {
            withMcpClient { client ->
                val result =
                    client.callTool(
                        toolCall(
                            "render_html",
                            buildJsonObject { put("html", SAMPLE_HTML) },
                        ),
                    )

                assertPdfResult(result)
            }
        }

    @Test
    fun renderUrlToolFetchesAndRendersHtml() =
        runBlocking {
            withMcpClient(fetchUrl = { FetchResult.Success(SAMPLE_HTML, it) }) { client ->
                val result =
                    client.callTool(
                        toolCall(
                            "render_url",
                            buildJsonObject { put("url", "https://example.com") },
                        ),
                    )

                assertPdfResult(result)
            }
        }

    @Test
    fun renderUrlToolReturnsFetchError() =
        runBlocking {
            withMcpClient(fetchUrl = { FetchResult.Failed("boom") }) { client ->
                val result =
                    client.callTool(
                        toolCall(
                            "render_url",
                            buildJsonObject { put("url", "https://example.com") },
                        ),
                    )

                assertEquals(true, result.isError)
                val structured = assertNotNull(result.structuredContent)
                assertEquals("fetch_failed", structured["error"]?.jsonPrimitive?.content)
            }
        }

    private fun assertPdfResult(result: CallToolResult) {
        assertFalse(result.isError == true)
        val structured = assertNotNull(result.structuredContent)
        assertEquals("application/pdf", structured["contentType"]?.jsonPrimitive?.content)
        assertEquals("output.pdf", structured["fileName"]?.jsonPrimitive?.content)
        assertFalse(structured["documentId"]?.jsonPrimitive?.contentOrNull.isNullOrBlank())
        val pdfBytes = Base64.getDecoder().decode(structured["pdfBase64"]?.jsonPrimitive?.content)
        assertTrue(
            pdfBytes
                .take(5)
                .toByteArray()
                .decodeToString()
                .startsWith("%PDF-"),
        )
    }

    private fun toolCall(
        name: String,
        arguments: JsonObject,
    ) = CallToolRequest(CallToolRequestParams(name = name, arguments = arguments))

    private suspend fun withMcpClient(
        fetchUrl: (String) -> FetchResult = { FetchResult.Failed("URL fetching is not configured") },
        block: suspend (Client) -> Unit,
    ) {
        val transports = ChannelTransport.createLinkedPair()
        val server = createPdfUaMcpServer(fetchUrl = fetchUrl)
        val session = server.createSession(transports.serverTransport)
        val client =
            mcpClient(
                clientInfo = Implementation("pdf-ua-api-test", "1.0.0"),
                clientOptions = ClientOptions(ClientCapabilities()),
                transport = transports.clientTransport,
            )

        try {
            block(client)
        } finally {
            client.close()
            session.close()
            server.close()
        }
    }

    private fun templateArguments() =
        buildJsonObject {
            putJsonObject("template") {
                put("version", 2)
                putJsonArray("rows") {
                    add(
                        buildJsonObject {
                            putJsonArray("blocks") {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", "Hello from MCP")
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }

    private companion object {
        const val SAMPLE_HTML =
            "<!DOCTYPE html><html lang=\"en\"><head><title>Test</title></head><body><h1>Test</h1></body></html>"
    }
}
