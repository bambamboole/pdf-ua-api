package bambamboole.pdf.api

import bambamboole.pdf.api.config.AppConfig
import bambamboole.pdf.api.routes.convertRoutes
import bambamboole.pdf.api.routes.healthRoutes
import bambamboole.pdf.api.routes.indexRoutes
import bambamboole.pdf.api.routes.validationRoutes
import bambamboole.pdf.api.services.PdfService
import bambamboole.pdf.api.services.PdfValidationService
import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val config = AppConfig.load(environment)

    PdfService.warmup()
    PdfValidationService.warmup()

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
        if (config.webUIEnabled) {
            indexRoutes()
        }
        healthRoutes()

        if (config.isAuthenticationEnabled) {
            authenticate("api-key-auth") {
                convertRoutes(config.pdfProducer)
                validationRoutes()
            }
        } else {
            convertRoutes(config.pdfProducer)
            validationRoutes()
        }
    }
}
