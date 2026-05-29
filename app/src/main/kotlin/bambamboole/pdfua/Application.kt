package bambamboole.pdfua

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.http.controller.convertAndValidateRoutes
import bambamboole.pdfua.http.controller.convertRoutes
import bambamboole.pdfua.http.controller.healthRoutes
import bambamboole.pdfua.http.controller.identifyRoutes
import bambamboole.pdfua.http.controller.indexRoutes
import bambamboole.pdfua.http.controller.renderImageRoutes
import bambamboole.pdfua.http.controller.renderRoutes
import bambamboole.pdfua.http.controller.templateBuilderWebRoutes
import bambamboole.pdfua.http.controller.templateSchemaRoutes
import bambamboole.pdfua.http.controller.validationRoutes
import bambamboole.pdfua.image.ImageRenderer
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfValidator
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.DocumentUploader
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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

fun Application.module(jwkProvider: JwkProvider? = null) {
    val config = AppConfig.load(environment)
    val version = loadVersion()
    LoggerFactory
        .getLogger("bambamboole.pdfua.Application")
        .info("PDF API version {} started", version)

    PdfRenderer.warmup()
    PdfValidator.warmup()
    ImageRenderer.warmup()

    val httpClient = AssetResolver.createHttpClient(config.assetTimeoutMs)
    val assetResolver =
        AssetResolver(
            httpClient = httpClient,
            timeoutMs = config.assetTimeoutMs,
            maxSizeBytes = config.assetMaxSizeBytes,
            allowedDomains = config.assetAllowedDomains,
        )

    val documentUploader =
        if (config.uploadEnabled) {
            DocumentUploader(
                httpClient = DocumentUploader.createHttpClient(config.assetTimeoutMs),
                timeoutMs = config.uploadTimeoutMs,
                allowedDomains = config.uploadAllowedDomains,
            )
        } else {
            null
        }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
            },
        )
    }

    if (config.webUIEnabled) {
        install(Mustache) {
            mustacheFactory = DefaultMustacheFactory("templates")
        }
    }

    install(CallLogging) {
        level = config.logLevel.slf4jLevel
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Unknown error")),
            )
        }
    }

    if (config.rateLimitTrustForwardedFor) {
        install(XForwardedHeaders)
    }

    if (config.rateLimitEnabled) {
        install(RateLimit) {
            register(RateLimitName("perIp")) {
                rateLimiter(limit = config.rateLimitPerIp, refillPeriod = config.rateLimitWindowSeconds.seconds)
                requestKey { call -> call.request.origin.remoteHost }
            }
            register(RateLimitName("global")) {
                rateLimiter(limit = config.rateLimitGlobal, refillPeriod = config.rateLimitWindowSeconds.seconds)
            }
        }
    }

    installAuthentication(config, jwkProvider)

    routing {
        swaggerUI(path = "api-docs", swaggerFile = "openapi/openapi.yaml")
        if (config.webUIEnabled) {
            indexRoutes()
            templateBuilderWebRoutes()
        }
        healthRoutes()
        templateSchemaRoutes()

        val providerName = authProviderName(config)
        if (providerName != null) {
            authenticate(providerName) {
                rateLimited(config) { expensiveRoutes(config, assetResolver, documentUploader) }
            }
        } else {
            rateLimited(config) { expensiveRoutes(config, assetResolver, documentUploader) }
        }
    }
}

private fun Route.expensiveRoutes(
    config: AppConfig,
    assetResolver: AssetResolver,
    uploader: DocumentUploader?,
) {
    convertRoutes(config.pdfProducer, assetResolver, uploader)
    renderRoutes(config.pdfProducer, assetResolver, uploader)
    validationRoutes()
    convertAndValidateRoutes(config.pdfProducer, assetResolver)
    renderImageRoutes(assetResolver, uploader)
    identifyRoutes()
}

private fun Route.rateLimited(
    config: AppConfig,
    block: Route.() -> Unit,
) {
    if (config.rateLimitEnabled) {
        rateLimit(RateLimitName("perIp")) {
            rateLimit(RateLimitName("global")) { block() }
        }
    } else {
        block()
    }
}

private const val JWKS_CACHE_SIZE = 10L
private const val JWKS_CACHE_EXPIRES_HOURS = 24L
private const val JWKS_RATE_LIMIT_BUCKET_SIZE = 10L
private const val JWKS_RATE_LIMIT_REFILL_MINUTES = 1L

private fun Application.installAuthentication(
    config: AppConfig,
    jwkProvider: JwkProvider?,
) {
    val jwtConfig = config.jwt
    when {
        jwtConfig != null -> {
            val resolvedJwkProvider = jwkProvider ?: buildJwkProvider(jwtConfig.jwksUrl)
            install(Authentication) {
                jwt("jwt-auth") {
                    verifier(resolvedJwkProvider, jwtConfig.issuer) {
                        jwtConfig.audience?.let { withAudience(it) }
                    }
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
        }

        config.apiKey != null -> {
            install(Authentication) {
                bearer("api-key-auth") {
                    authenticate { credential ->
                        if (credential.token == config.apiKey) {
                            UserIdPrincipal("api-user")
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }
}

private fun authProviderName(config: AppConfig): String? =
    when {
        config.jwt != null -> "jwt-auth"
        config.apiKey != null -> "api-key-auth"
        else -> null
    }

private fun buildJwkProvider(jwksUrl: String): JwkProvider =
    JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(JWKS_CACHE_SIZE, JWKS_CACHE_EXPIRES_HOURS, TimeUnit.HOURS)
        .rateLimited(JWKS_RATE_LIMIT_BUCKET_SIZE, JWKS_RATE_LIMIT_REFILL_MINUTES, TimeUnit.MINUTES)
        .build()
