package bambamboole.pdfua

import bambamboole.pdfua.http.ConvertRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApplicationConfigTest {
    @Test
    fun testCustomPdfProducer() =
        testApplication {
            environment {
                config = MapApplicationConfig("pdf.producer" to "custom-producer-v1.0")
            }
            application {
                module()
            }

            val html =
                """<!DOCTYPE html><html lang="en"><head><title>Test</title><meta name="subject" content="Test"/></head><body><h1>Test</h1></body></html>"""

            val requestBody = Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(html))

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())

            Loader.loadPDF(response.bodyAsBytes()).use { document ->
                assertEquals("custom-producer-v1.0", document.documentInformation.producer)
            }
        }

    @Test
    fun testPartialJwtConfigFailsToStart() =
        testApplication {
            environment {
                config = MapApplicationConfig("jwt.issuer" to "https://issuer.test")
            }
            application {
                module()
            }

            assertFailsWith<IllegalStateException> { startApplication() }
        }

    @Test
    fun testJwksUrlWithoutIssuerFailsToStart() =
        testApplication {
            environment {
                config = MapApplicationConfig("jwt.jwksUrl" to "https://issuer.test/.well-known/jwks.json")
            }
            application {
                module()
            }

            val exception = assertFailsWith<IllegalStateException> { startApplication() }
            assertTrue(exception.message!!.contains("JWT_ISSUER"))
        }

    @Test
    fun yamlTrustPrivateHostsDefaultMatchesAppConfigDefault() {
        val yaml = ApplicationConfigTest::class.java.getResource("/application.yaml")!!.readText()
        val match = Regex("trustPrivateHosts:\\s*\"\\\$TRUST_PRIVATE_HOSTS:(true|false)\"").find(yaml)
        assertEquals(
            "true",
            match?.groupValues?.get(1),
            "application.yaml TRUST_PRIVATE_HOSTS default must match AppConfig.Defaults.TRUST_PRIVATE_HOSTS",
        )
    }
}
