package bambamboole.pdfua.http.controller

import bambamboole.pdfua.module
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsRoutesTest {
    @Test
    fun preflightAllowsConfiguredOrigin() =
        testApplication {
            environment { config = MapApplicationConfig("cors.allowedOrigins" to "localhost:4321") }
            application { module() }

            val response =
                client.options("/convert") {
                    header(HttpHeaders.Origin, "http://localhost:4321")
                    header(HttpHeaders.AccessControlRequestMethod, "POST")
                }

            assertEquals("http://localhost:4321", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun preflightRejectsUnconfiguredOrigin() =
        testApplication {
            environment { config = MapApplicationConfig("cors.allowedOrigins" to "localhost:4321") }
            application { module() }

            val response =
                client.options("/convert") {
                    header(HttpHeaders.Origin, "http://evil.test")
                    header(HttpHeaders.AccessControlRequestMethod, "POST")
                }

            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun noCorsHeadersWhenUnset() =
        testApplication {
            application { module() }

            val response =
                client.options("/convert") {
                    header(HttpHeaders.Origin, "http://localhost:4321")
                    header(HttpHeaders.AccessControlRequestMethod, "POST")
                }

            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
}
