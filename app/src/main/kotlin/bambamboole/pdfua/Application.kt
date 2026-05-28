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
import bambamboole.pdfua.services.AssetResolver
import bambamboole.pdfua.image.ImageRenderer
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfValidator
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Properties

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

private fun loadVersion(): String {
    val props = Properties()
    Thread.currentThread().contextClassLoader
        .getResourceAsStream("version.properties")
        ?.use { props.load(it) }
    return props.getProperty("version", "dev")
}

fun Application.module() {
    val config = AppConfig.load(environment)
    val version = loadVersion()
    LoggerFactory.getLogger("bambamboole.pdfua.Application")
        .info("PDF API version {} started", version)

    PdfRenderer.warmup()
    PdfValidator.warmup()
    ImageRenderer.warmup()

    val httpClient = AssetResolver.createHttpClient(config.assetTimeoutMs)
    val assetResolver = AssetResolver(
        httpClient = httpClient,
        timeoutMs = config.assetTimeoutMs,
        maxSizeBytes = config.assetMaxSizeBytes,
        allowedDomains = config.assetAllowedDomains
    )

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
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
                mapOf("error" to (cause.message ?: "Unknown error"))
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
