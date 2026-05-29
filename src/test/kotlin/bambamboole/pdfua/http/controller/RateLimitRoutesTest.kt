package bambamboole.pdfua.http.controller

import bambamboole.pdfua.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitRoutesTest {
    private val blankConvertBody = """{"html":""}"""

    private suspend fun ApplicationTestBuilder.postConvert(forwardedFor: String? = null) =
        client.post("/convert") {
            contentType(ContentType.Application.Json)
            forwardedFor?.let { header("X-Forwarded-For", it) }
            setBody(blankConvertBody)
        }

    @Test
    fun perIpLimitExceededReturns429() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "rateLimit.perIp" to "2",
                        "rateLimit.global" to "100",
                        "rateLimit.trustForwardedFor" to "true",
                    )
            }
            application { module() }

            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.1").status)
            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.1").status)

            val limited = postConvert("10.0.0.1")
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertNotNull(limited.headers[HttpHeaders.RetryAfter], "429 response must include Retry-After")
        }

    @Test
    fun differentIpsHaveSeparateBuckets() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "rateLimit.perIp" to "1",
                        "rateLimit.global" to "100",
                        "rateLimit.trustForwardedFor" to "true",
                    )
            }
            application { module() }

            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.1").status)
            assertEquals(HttpStatusCode.TooManyRequests, postConvert("10.0.0.1").status)

            // A different IP still has its full budget.
            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.2").status)
        }

    @Test
    fun globalCapAppliesAcrossIps() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "rateLimit.perIp" to "100",
                        "rateLimit.global" to "2",
                        "rateLimit.trustForwardedFor" to "true",
                    )
            }
            application { module() }

            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.1").status)
            assertEquals(HttpStatusCode.BadRequest, postConvert("10.0.0.2").status)

            // Fresh IP, but the global bucket is exhausted.
            assertEquals(HttpStatusCode.TooManyRequests, postConvert("10.0.0.3").status)
        }

    @Test
    fun disabledAllowsUnlimitedRequests() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "rateLimit.enabled" to "false",
                        "rateLimit.perIp" to "1",
                    )
            }
            application { module() }

            repeat(5) {
                assertEquals(HttpStatusCode.BadRequest, postConvert().status)
            }
        }

    @Test
    fun healthIsNeverRateLimited() =
        testApplication {
            environment {
                config =
                    MapApplicationConfig(
                        "rateLimit.perIp" to "1",
                        "rateLimit.global" to "1",
                    )
            }
            application { module() }

            repeat(5) {
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
            }
        }
}
