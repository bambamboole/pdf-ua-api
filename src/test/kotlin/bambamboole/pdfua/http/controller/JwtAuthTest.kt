package bambamboole.pdfua.http.controller

import bambamboole.pdfua.auth.JwtTestSupport
import bambamboole.pdfua.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class JwtAuthTest {
    private val issuer = "https://issuer.test"
    private val audience = "pdf-ua-api"
    private val jwksUrl = "https://issuer.test/.well-known/jwks.json"
    private val html = "<html><body><h1>Test</h1></body></html>"

    private fun jwtConfig(withAudience: Boolean = false) =
        if (withAudience) {
            MapApplicationConfig(
                "jwt.issuer" to issuer,
                "jwt.jwksUrl" to jwksUrl,
                "jwt.audience" to audience,
            )
        } else {
            MapApplicationConfig(
                "jwt.issuer" to issuer,
                "jwt.jwksUrl" to jwksUrl,
            )
        }

    private suspend fun ApplicationTestBuilder.postConvert(authHeader: String?) =
        client.post("/convert") {
            contentType(ContentType.Application.Json)
            authHeader?.let { header(HttpHeaders.Authorization, it) }
            setBody("""{"html":"$html"}""")
        }

    @Test
    fun validTokenReturns200() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.token(issuer)}")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }

    @Test
    fun tamperedSignatureReturns401() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.tokenWithWrongSignature(issuer)}")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun expiredTokenReturns401() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val expired = JwtTestSupport.token(issuer, expiresAt = Date(System.currentTimeMillis() - 1_000))
            val response = postConvert("Bearer $expired")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun wrongIssuerReturns401() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.token("https://evil.test")}")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun audienceRequiredMissingReturns401() =
        testApplication {
            environment { config = jwtConfig(withAudience = true) }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.token(issuer)}")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun audienceRequiredWrongReturns401() =
        testApplication {
            environment { config = jwtConfig(withAudience = true) }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.token(issuer, audience = "other")}")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun audienceRequiredCorrectReturns200() =
        testApplication {
            environment { config = jwtConfig(withAudience = true) }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("Bearer ${JwtTestSupport.token(issuer, audience = audience)}")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun missingHeaderReturns401() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert(null)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun malformedHeaderReturns401() =
        testApplication {
            environment { config = jwtConfig() }
            application { module(JwtTestSupport.jwkProvider) }

            val response = postConvert("InvalidFormat some-token")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun jwtTakesPrecedenceOverApiKey() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "jwt.issuer" to issuer,
                        "jwt.jwksUrl" to jwksUrl,
                        "api.key" to "test-api-key",
                    )
            }
            application { module(JwtTestSupport.jwkProvider) }

            val validJwt = postConvert("Bearer ${JwtTestSupport.token(issuer)}")
            assertEquals(HttpStatusCode.OK, validJwt.status)

            val rawApiKey = postConvert("Bearer test-api-key")
            assertEquals(HttpStatusCode.Unauthorized, rawApiKey.status)
        }
}
