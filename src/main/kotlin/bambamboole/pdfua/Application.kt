package bambamboole.pdfua

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.http.controller.convert
import bambamboole.pdfua.http.controller.convertAndValidate
import bambamboole.pdfua.http.controller.health
import bambamboole.pdfua.http.controller.identify
import bambamboole.pdfua.http.controller.render
import bambamboole.pdfua.http.controller.renderImage
import bambamboole.pdfua.http.controller.templateSchema
import bambamboole.pdfua.http.controller.validation
import bambamboole.pdfua.image.ImageRenderer
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfValidator
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
import bambamboole.pdfua.services.HtmlSourceFetcher
import bambamboole.pdfua.services.validatePublicHttpUrl
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Properties
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

private fun loadVersion(): String {
    val props = Properties()
    Thread
        .currentThread()
        .contextClassLoader
        .getResourceAsStream("version.properties")
        ?.use { props.load(it) }
    return props.getProperty("version", "dev")
}

fun Application.bootstrap(jwkProvider: JwkProvider? = null) {
    val config = AppConfig.load(environment)
    val version = loadVersion()
    LoggerFactory
        .getLogger("bambamboole.pdfua.Application")
        .info("PDF API version {} started", version)

    PdfRenderer.warmup()
    PdfValidator.warmup()
    ImageRenderer.warmup()

    val trustPrivate = config.trustPrivateHosts
    val validateUrl: (URI, Set<String>) -> Unit = { uri, domains ->
        validatePublicHttpUrl(uri, domains, trustPrivateHosts = trustPrivate)
    }

    val httpClient = AssetResolver.createHttpClient(config.assetTimeoutMs)
    val assetResolver =
        AssetResolver(
            httpClient = httpClient,
            timeoutMs = config.assetTimeoutMs,
            maxSizeBytes = config.assetMaxSizeBytes,
            allowedDomains = config.assetAllowedDomains,
            validateUrl = validateUrl,
        )

    val documentUploader: DocumentUploader? =
        if (config.uploadEnabled) {
            DocumentUploader(
                httpClient = DocumentUploader.createHttpClient(config.assetTimeoutMs),
                timeoutMs = config.uploadTimeoutMs,
                allowedDomains = config.uploadAllowedDomains,
                validateUrl = validateUrl,
            )
        } else {
            null
        }

    val htmlSourceFetcher =
        HtmlSourceFetcher(
            httpClient = HtmlSourceFetcher.createHttpClient(config.assetTimeoutMs),
            timeoutMs = config.assetTimeoutMs,
            maxSizeBytes = config.assetMaxSizeBytes,
            allowedDomains = config.assetAllowedDomains,
            validateUrl = validateUrl,
        )

    val resolvedJwkProvider: JwkProvider? =
        config.jwt?.let { jwkProvider ?: buildJwkProvider(it.jwksUrl) }

    dependencies {
        provide<AppConfig> { config }
        provide<AssetResolver> { assetResolver }
        provide<DocumentUploader?> { documentUploader }
        provide<HtmlSourceFetcher> { htmlSourceFetcher }
        provide<JwkProvider?> { resolvedJwkProvider }
    }
}

fun authProviderName(config: AppConfig): String? =
    when {
        config.jwt != null -> "jwt-auth"
        config.apiKey != null -> "api-key-auth"
        else -> null
    }

fun Route.expensiveRoute(
    config: AppConfig,
    block: Route.() -> Unit,
) {
    val provider = authProviderName(config)
    val rateLimited: Route.(Route.() -> Unit) -> Unit = { inner ->
        if (config.rateLimitEnabled) {
            rateLimit(RateLimitName("perIp")) {
                rateLimit(RateLimitName("global")) { inner() }
            }
        } else {
            inner()
        }
    }
    if (provider != null) {
        authenticate(provider) { rateLimited { block() } }
    } else {
        rateLimited { block() }
    }
}

/**
 * Test-friendly aggregator: installs every per-feature module in the same order
 * production reads from `application.yaml`. Production never calls this — Ktor
 * loads each module individually from the YAML modules list.
 */
fun Application.module(jwkProvider: JwkProvider? = null) {
    bootstrap(jwkProvider)
    logging()
    serialization()
    statusPages()
    cors()
    rateLimit()
    auth()
    swagger()
    health()
    templateSchema()
    convert()
    render()
    validation()
    convertAndValidate()
    renderImage()
    identify()
}

private const val JWKS_CACHE_SIZE = 10L
private const val JWKS_CACHE_EXPIRES_HOURS = 24L
private const val JWKS_RATE_LIMIT_BUCKET_SIZE = 10L
private const val JWKS_RATE_LIMIT_REFILL_MINUTES = 1L

private fun buildJwkProvider(jwksUrl: String): JwkProvider =
    JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(JWKS_CACHE_SIZE, JWKS_CACHE_EXPIRES_HOURS, TimeUnit.HOURS)
        .rateLimited(JWKS_RATE_LIMIT_BUCKET_SIZE, JWKS_RATE_LIMIT_REFILL_MINUTES, TimeUnit.MINUTES)
        .build()
