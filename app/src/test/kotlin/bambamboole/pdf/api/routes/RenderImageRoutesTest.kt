package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.RenderImageRequest
import bambamboole.pdf.api.module
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RenderImageRoutesTest {

    companion object {
        private fun getSourceFixturesDir(): File {
            val fixturesUrl = RenderImageRoutesTest::class.java.classLoader.getResource("image-fixtures")
                ?: fail("image-fixtures directory not found in classpath")
            val buildFixturesDir = File(fixturesUrl.toURI())
            val projectRoot = buildFixturesDir.absolutePath.substringBefore("/app/build/")
            return File(projectRoot, "app/src/test/resources/image-fixtures")
        }

        private fun imagesAreIdentical(expected: BufferedImage, actual: BufferedImage): Boolean {
            if (expected.width != actual.width || expected.height != actual.height) return false
            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    if (expected.getRGB(x, y) != actual.getRGB(x, y)) return false
                }
            }
            return true
        }

        private suspend fun ApplicationTestBuilder.testImageFixture(
            fixtureName: String,
            format: String = "png",
            width: Int = 800
        ) {
            println("\n=== Testing image fixture: $fixtureName ===")

            val fixtureDir = File(getSourceFixturesDir(), fixtureName)
            assertTrue(
                fixtureDir.exists() && fixtureDir.isDirectory,
                "Fixture directory not found: ${fixtureDir.absolutePath}"
            )

            val inputHtml = File(fixtureDir, "input.html").readText()
            val expectedFile = File(fixtureDir, "expected.$format")

            val response = client.post("/render") {
                contentType(ContentType.Application.Json)
                setBody(
                    Json.encodeToString(
                        RenderImageRequest.serializer(),
                        RenderImageRequest(html = inputHtml, format = format, width = width)
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status, "Fixture '$fixtureName': should return 200 OK")

            val actualBytes = response.readRawBytes()
            assertTrue(actualBytes.isNotEmpty(), "Fixture '$fixtureName': image should not be empty")

            val generatedFile = File(fixtureDir, "generated.$format")
            if (generatedFile.exists()) generatedFile.delete()
            generatedFile.writeBytes(actualBytes)
            println("Fixture '$fixtureName': Generated image saved to ${generatedFile.absolutePath}")

            if (expectedFile.exists()) {
                val expectedImage = ImageIO.read(expectedFile)
                val actualImage = ImageIO.read(ByteArrayInputStream(actualBytes))

                if (!imagesAreIdentical(expectedImage, actualImage)) {
                    val diffImage = PdfVisualTester.createDiffImage(expectedImage, actualImage)
                    val diffFile = File(fixtureDir, "diff.$format")
                    ImageIO.write(diffImage, format, diffFile)
                    println("  Diff image saved: ${diffFile.name}")
                    fail(
                        "Fixture '$fixtureName': Visual regression detected. " +
                            "See diff at ${diffFile.absolutePath}"
                    )
                }
                println("Fixture '$fixtureName': Visual regression test passed - images are identical")
            } else {
                println("Fixture '$fixtureName': No expected.$format found, saving as baseline")
                File(fixtureDir, "expected.$format").writeBytes(actualBytes)
                println("Fixture '$fixtureName': Baseline saved to ${expectedFile.absolutePath}")
            }
        }
    }

    // ========================================
    // Basic Endpoint Tests
    // ========================================

    @Test
    fun testRenderPng() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Hello</h1></body></html>"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG, response.contentType())
        assertTrue(response.headers.contains(HttpHeaders.ContentDisposition))

        val bytes = response.readRawBytes()
        assertTrue(bytes.size > 100)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1]) // P
        assertEquals(0x4E.toByte(), bytes[2]) // N
        assertEquals(0x47.toByte(), bytes[3]) // G
    }

    @Test
    fun testRenderJpg() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Hello</h1></body></html>","format":"jpg"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.JPEG, response.contentType())

        val bytes = response.readRawBytes()
        assertTrue(bytes.size > 100)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun testRenderWithCustomWidth() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Hello</h1></body></html>","width":1200}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG, response.contentType())
    }

    @Test
    fun testRenderEmptyHtml() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testRenderInvalidFormat() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Hello</h1></body></html>","format":"webp"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Unsupported format"))
    }

    @Test
    fun testRenderInvalidWidth() = testApplication {
        application { module() }

        val response = client.post("/render") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Hello</h1></body></html>","width":5000}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Width must be between"))
    }

    // ========================================
    // Fixture-Based Visual Regression Tests
    // ========================================

    @Test
    fun testFixtureSimpleHeading() = testApplication {
        application { module() }
        testImageFixture("simple-heading")
    }

    @Test
    fun testFixtureStyledContent() = testApplication {
        application { module() }
        testImageFixture("styled-content")
    }

    @Test
    fun testFixtureColoredBoxes() = testApplication {
        application { module() }
        testImageFixture("colored-boxes")
    }
}
