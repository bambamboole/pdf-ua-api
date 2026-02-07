package bambamboole.pdf.api

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import bambamboole.pdf.api.routes.healthRoutes
import bambamboole.pdf.api.routes.convertRoutes
import bambamboole.pdf.api.routes.validationRoutes
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val logger = log
    val apiKey = environment.config.propertyOrNull("api.key")?.getString()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Unknown error"))
            )
        }
    }

    // Install authentication only if API key is configured
    if (apiKey != null) {
        install(Authentication) {
            bearer("api-key-auth") {
                authenticate { credential ->
                    if (credential.token == apiKey) {
                        UserIdPrincipal("api-user")
                    } else {
                        null
                    }
                }
            }
        }
        logger.info("API key authentication enabled")
    } else {
        logger.warn("API key not configured - running without authentication")
    }

    routing {
        healthRoutes()

        // Conditionally protect routes based on whether API key is configured
        if (apiKey != null) {
            authenticate("api-key-auth") {
                convertRoutes()
                validationRoutes()
            }
        } else {
            convertRoutes()
            validationRoutes()
        }
    }
}
