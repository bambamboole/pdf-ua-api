package bambamboole.pdfua

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.http.BEARER_SECURITY_SCHEME
import bambamboole.pdfua.http.openApiSpec
import bambamboole.pdfua.http.serializeOpenApiDoc
import com.auth0.jwk.JwkProvider
import io.ktor.http.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Application.logging() {
    val config: AppConfig by dependencies
    install(CallLogging) {
        level = config.logLevel.slf4jLevel
    }
}

fun Application.serialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
            },
        )
    }
}

fun Application.statusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Invalid request")),
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Unknown error")),
            )
        }
    }
}

fun Application.cors() {
    val config: AppConfig by dependencies
    if (config.corsAllowedOrigins.isEmpty()) return
    install(CORS) {
        config.corsAllowedOrigins.forEach { host ->
            allowHost(host, schemes = listOf("http", "https"))
        }
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader("X-Upload-Url")
    }
}

fun Application.rateLimit() {
    val config: AppConfig by dependencies
    if (config.rateLimitTrustForwardedFor) {
        install(XForwardedHeaders)
    }
    if (!config.rateLimitEnabled) return
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

fun Application.auth() {
    val config: AppConfig by dependencies
    val jwkProvider: JwkProvider? by dependencies
    val jwtConfig = config.jwt
    when {
        jwtConfig != null -> {
            install(Authentication) {
                jwt("jwt-auth") {
                    verifier(requireNotNull(jwkProvider) { "JWT enabled but JwkProvider missing" }, jwtConfig.issuer) {
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

fun Application.swagger() {
    val version = loadVersion()
    openApiSpec(version)
    routing {
        swaggerUI(path = "api-docs") {
            source =
                OpenApiDocSource.Routing(
                    contentType = ContentType.Application.Json,
                    serializeModel = ::serializeOpenApiDoc,
                )
            info =
                OpenApiInfo(
                    title = "PDF API",
                    version = version,
                    description =
                        "HTML to PDF/A-3a conversion API with PDF/UA accessibility support and veraPDF validation",
                )
            security { requirement(BEARER_SECURITY_SCHEME) }
        }
    }
}
