package bambamboole.pdfua.routes

import bambamboole.pdfua.module
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {
    @Test
    fun testHealthEndpoint() =
        testApplication {
            application {
                module()
            }

            client.get("/health").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertTrue(bodyAsText().contains("\"status\""))
                assertTrue(bodyAsText().contains("\"ok\""))
            }
        }

    @Test
    fun testHealthEndpointReturnType() =
        testApplication {
            application {
                module()
            }

            val response = client.get("/health")
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun testHealthEndpointWorksWithoutAuthenticationEvenWhenAuthEnabled() =
        testApplication {
            environment {
                config = MapApplicationConfig("api.key" to "test-api-key")
            }
            application {
                module()
            }

            // Health endpoint should work without authentication
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"status\""))
            assertTrue(response.bodyAsText().contains("\"ok\""))
        }
}
