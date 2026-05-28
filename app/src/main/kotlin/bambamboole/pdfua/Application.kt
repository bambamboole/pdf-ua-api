package bambamboole.pdfua

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.routes.convertAndValidateRoutes
import bambamboole.pdfua.routes.convertRoutes
import bambamboole.pdfua.routes.healthRoutes
import bambamboole.pdfua.routes.identifyRoutes
import bambamboole.pdfua.routes.indexRoutes
import bambamboole.pdfua.routes.renderImageRoutes
import bambamboole.pdfua.routes.renderRoutes
import bambamboole.pdfua.routes.templateBuilderWebRoutes
import bambamboole.pdfua.routes.templateSchemaRoutes
import bambamboole.pdfua.routes.validationRoutes
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.services.ImageRenderService
import bambamboole.pdfua.services.PdfService
import bambamboole.pdfua.services.PdfValidationService
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.mustache.Mustache
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Properties

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

fun Application.module() {
    val config = AppConfig.load(environment)
    val version = loadVersion()
    LoggerFactory
        .getLogger("bambamboole.pdfua.Application")
        .info("PDF API version {} started", version)

    PdfService.warmup()
    PdfValidationService.warmup()
    ImageRenderService.warmup()

    val httpClient = AssetResolver.createHttpClient(config.assetTimeoutMs)
    val assetResolver =
        AssetResolver(
            httpClient = httpClient,
            timeoutMs = config.assetTimeoutMs,
            maxSizeBytes = config.assetMaxSizeBytes,
            allowedDomains = config.assetAllowedDomains,
        )

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

    if (config.isAuthenticationEnabled) {
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

    routing {
        swaggerUI(path = "api-docs", swaggerFile = "openapi/openapi.yaml")
        if (config.webUIEnabled) {
            indexRoutes()
            templateBuilderWebRoutes()
        }
        healthRoutes()
        templateSchemaRoutes()

        if (config.isAuthenticationEnabled) {
            authenticate("api-key-auth") {
                convertRoutes(config.pdfProducer, assetResolver)
                renderRoutes(config.pdfProducer, assetResolver)
                validationRoutes()
                convertAndValidateRoutes(config.pdfProducer, assetResolver)
                renderImageRoutes(assetResolver)
                identifyRoutes()
            }
        } else {
            convertRoutes(config.pdfProducer, assetResolver)
            renderRoutes(config.pdfProducer, assetResolver)
            validationRoutes()
            convertAndValidateRoutes(config.pdfProducer, assetResolver)
            renderImageRoutes(assetResolver)
            identifyRoutes()
        }
    }
}
