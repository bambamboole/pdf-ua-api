package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    @Test
    fun testHealthEndpoint() = testApplication {
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
    fun testHealthEndpointReturnType() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun testHealthEndpointWorksWithoutAuthenticationEvenWhenAuthEnabled() = testApplication {
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
