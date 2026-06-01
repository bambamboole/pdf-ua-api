package bambamboole.pdfua.mcp

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.http.RenderHtmlRequest
import bambamboole.pdfua.http.controller.RenderRequest
import bambamboole.pdfua.http.controller.RenderUrlRequest
import bambamboole.pdfua.http.controller.validateBaseUrl
import bambamboole.pdfua.pdf.PdfRenderOptions
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfResult
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.FetchResult
import bambamboole.pdfua.services.HtmlSourceFetcher
import bambamboole.pdfua.services.TemplatePdfRenderResult
import bambamboole.pdfua.services.TemplatePdfRenderService
import bambamboole.pdfua.template.FileAttachment
import bambamboole.pdfua.template.TemplateJsonSchema
import bambamboole.pdfua.template.ValidationIssue
import bambamboole.pdfua.template.toJsonValidationIssue
import com.openhtmltopdf.extend.FSStreamFactory
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

private const val SERVER_NAME = "pdf-ua-api"
private const val SERVER_VERSION = "dev"
private const val RENDER_TEMPLATE_TOOL = "render_template"
private const val RENDER_HTML_TOOL = "render_html"
private const val RENDER_URL_TOOL = "render_url"
private const val JSON_SCHEMA_DRAFT = "https://json-schema.org/draft/2020-12/schema"

private val toolJson =
    Json {
        prettyPrint = true
        isLenient = true
    }

private val embedColorProfileSchema: JsonObject =
    buildJsonObject {
        put("type", "boolean")
        put("default", true)
    }

private val fileAttachmentsSchema: JsonObject =
    buildJsonObject {
        put("type", "array")
        put("description", "Files to embed in the PDF/A-3 container.")
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name") { put("type", "string") }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Base64-encoded file content.")
                }
                putJsonObject("mimeType") { put("type", "string") }
                putJsonObject("description") { put("type", "string") }
                putJsonObject("relationship") { put("type", "string") }
            }
            putJsonArray("required") {
                add("name")
                add("content")
            }
        }
    }

private val renderTemplateInputSchema: ToolSchema =
    ToolSchema(
        schema = JSON_SCHEMA_DRAFT,
        properties =
            buildJsonObject {
                put("template", TemplateJsonSchema.current())
                putJsonObject("data") {
                    put("type", "object")
                    put("description", "Per-block content overrides, keyed by block id.")
                    put("additionalProperties", true)
                }
            },
        required = listOf("template"),
    )

private val renderHtmlInputSchema: ToolSchema =
    ToolSchema(
        schema = JSON_SCHEMA_DRAFT,
        properties =
            buildJsonObject {
                putJsonObject("html") {
                    put("type", "string")
                    put("description", "HTML markup to render.")
                }
                putJsonObject("baseUrl") {
                    put("type", "string")
                    put("description", "Base URL for resolving relative asset references (http or https).")
                }
                put("attachments", fileAttachmentsSchema)
                put("embedColorProfile", embedColorProfileSchema)
            },
        required = listOf("html"),
    )

private val renderUrlInputSchema: ToolSchema =
    ToolSchema(
        schema = JSON_SCHEMA_DRAFT,
        properties =
            buildJsonObject {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "Public http or https URL whose HTML should be rendered.")
                }
                put("attachments", fileAttachmentsSchema)
                put("embedColorProfile", embedColorProfileSchema)
            },
        required = listOf("url"),
    )

private val renderOutputSchema: ToolSchema =
    ToolSchema(
        schema = JSON_SCHEMA_DRAFT,
        properties =
            buildJsonObject {
                putJsonObject("documentId") { put("type", "string") }
                putJsonObject("contentType") { put("const", "application/pdf") }
                putJsonObject("fileName") { put("const", "output.pdf") }
                putJsonObject("pdfBase64") { put("type", "string") }
            },
        required = listOf("documentId", "contentType", "fileName", "pdfBase64"),
    )

fun Application.mcpServer() {
    val config: AppConfig by dependencies
    val assetResolver: AssetResolver by dependencies
    val htmlSourceFetcher: HtmlSourceFetcher by dependencies

    install(SSE)
    routing {
        expensiveRoute(config) {
            mcp("/mcp") {
                createPdfUaMcpServer(config.pdfProducer, assetResolver, htmlSourceFetcher::fetch)
            }
        }
    }
}

internal fun createPdfUaMcpServer(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null,
    fetchUrl: (String) -> FetchResult,
): Server =
    Server(
        serverInfo = Implementation(SERVER_NAME, SERVER_VERSION),
        options = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools())),
        instructions =
            "Render PDF/A-3a and PDF/UA documents: render_template renders a pdf-ua-api JSON template, " +
                "render_html renders raw HTML, and render_url fetches a URL and renders its HTML.",
    ) {
        val templatePdfRenderer = TemplatePdfRenderService(pdfProducer, assetResolver)

        addTool(
            name = RENDER_TEMPLATE_TOOL,
            description = "Render a pdf-ua-api JSON template to a PDF/A-3a and PDF/UA document.",
            inputSchema = renderTemplateInputSchema,
            outputSchema = renderOutputSchema,
        ) { request -> handleRenderTemplate(request, templatePdfRenderer) }

        addTool(
            name = RENDER_HTML_TOOL,
            description = "Render raw HTML to a PDF/A-3a and PDF/UA document.",
            inputSchema = renderHtmlInputSchema,
            outputSchema = renderOutputSchema,
        ) { request -> handleRenderHtml(request, pdfProducer, assetResolver) }

        addTool(
            name = RENDER_URL_TOOL,
            description = "Fetch the HTML at a URL and render it to a PDF/A-3a and PDF/UA document.",
            inputSchema = renderUrlInputSchema,
            outputSchema = renderOutputSchema,
        ) { request -> handleRenderUrl(request, pdfProducer, assetResolver, fetchUrl) }
    }

private fun handleRenderTemplate(
    request: CallToolRequest,
    templatePdfRenderer: TemplatePdfRenderService,
): CallToolResult =
    withDecodedArguments<RenderRequest>(request) { renderRequest ->
        renderCatching {
            when (val result = templatePdfRenderer.render(renderRequest.template, renderRequest.data)) {
                is TemplatePdfRenderResult.Success -> successResult(result.pdf)
                is TemplatePdfRenderResult.ValidationFailed -> validationErrorResult(result.issues)
            }
        }
    }

private fun handleRenderHtml(
    request: CallToolRequest,
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
): CallToolResult =
    withDecodedArguments<RenderHtmlRequest>(request) { renderRequest ->
        renderCatching {
            renderHtmlToPdf(
                html = renderRequest.html,
                baseUrl = renderRequest.baseUrl?.also(::validateBaseUrl) ?: "",
                attachments = renderRequest.attachments,
                embedColorProfile = renderRequest.embedColorProfile,
                pdfProducer = pdfProducer,
                assetResolver = assetResolver,
            )
        }
    }

private fun handleRenderUrl(
    request: CallToolRequest,
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
    fetchUrl: (String) -> FetchResult,
): CallToolResult =
    withDecodedArguments<RenderUrlRequest>(request) { renderRequest ->
        when (val fetch = fetchUrl(renderRequest.url)) {
            is FetchResult.Failure -> {
                errorResult("fetch_failed", fetch.message)
            }

            is FetchResult.Success -> {
                renderCatching {
                    renderHtmlToPdf(
                        html = fetch.html,
                        baseUrl = fetch.finalUrl,
                        attachments = renderRequest.attachments,
                        embedColorProfile = renderRequest.embedColorProfile,
                        pdfProducer = pdfProducer,
                        assetResolver = assetResolver,
                    )
                }
            }
        }
    }

private fun renderHtmlToPdf(
    html: String,
    baseUrl: String,
    attachments: List<FileAttachment>?,
    embedColorProfile: Boolean,
    pdfProducer: String,
    assetResolver: FSStreamFactory?,
): CallToolResult =
    successResult(
        PdfRenderer.convertHtmlToPdf(
            html = html,
            producer = pdfProducer,
            assetResolver = assetResolver,
            baseUrl = baseUrl,
            attachments = attachments,
            options = PdfRenderOptions(embedColorProfile = embedColorProfile),
        ),
    )

private inline fun <reified T> withDecodedArguments(
    request: CallToolRequest,
    render: (T) -> CallToolResult,
): CallToolResult {
    val arguments =
        request.params.arguments ?: return errorResult("missing_arguments", "Tool arguments are required.")
    val decoded =
        try {
            toolJson.decodeFromJsonElement<T>(arguments)
        } catch (e: SerializationException) {
            return validationErrorResult(listOf(e.toJsonValidationIssue("Invalid tool arguments")))
        } catch (e: IllegalArgumentException) {
            return errorResult("invalid_arguments", e.message ?: "Invalid tool arguments.")
        }
    return render(decoded)
}

@Suppress("TooGenericExceptionCaught")
private inline fun renderCatching(block: () -> CallToolResult): CallToolResult =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        errorResult("invalid_arguments", e.message ?: "Invalid tool arguments.")
    } catch (e: Exception) {
        errorResult("render_failed", e.message ?: "Rendering failed.")
    }

private fun successResult(pdf: PdfResult): CallToolResult =
    toolResult(
        RenderToolResponse(
            documentId = pdf.documentId,
            pdfBase64 = Base64.getEncoder().encodeToString(pdf.bytes),
        ),
        isError = false,
    )

private fun validationErrorResult(issues: List<ValidationIssue>): CallToolResult {
    val error = RenderToolError(error = "validation_failed", issues = issues)
    return toolResult(error, isError = true)
}

private fun errorResult(
    code: String,
    message: String,
): CallToolResult = toolResult(RenderToolError(error = code, message = message), isError = true)

private inline fun <reified T> toolResult(
    payload: T,
    isError: Boolean,
): CallToolResult {
    val structured = toolJson.encodeToJsonElement(payload).jsonObject
    return CallToolResult(
        content = listOf(TextContent(toolJson.encodeToString(structured))),
        isError = isError,
        structuredContent = structured,
    )
}

@Serializable
private data class RenderToolResponse(
    val documentId: String,
    @EncodeDefault val contentType: String = "application/pdf",
    @EncodeDefault val fileName: String = "output.pdf",
    val pdfBase64: String,
)

@Serializable
private data class RenderToolError(
    val error: String,
    val message: String? = null,
    val issues: List<ValidationIssue>? = null,
)
