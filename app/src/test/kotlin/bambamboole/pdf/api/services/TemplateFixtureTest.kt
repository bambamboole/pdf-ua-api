package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.RenderRequest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TemplateFixtureTest {

    companion object {
        private val json = Json

        private fun sourceFixturesDir(): File {
            val url = TemplateFixtureTest::class.java.classLoader.getResource("fixtures/template")
                ?: fail("fixtures/template directory not found in classpath")
            val buildDir = File(url.toURI())
            val projectRoot = buildDir.absolutePath.substringBefore("/app/build/")
            return File(projectRoot, "app/src/test/resources/fixtures/template")
        }

        private fun assertTemplateFixture(name: String) {
            val dir = File(sourceFixturesDir(), name)
            assertTrue(dir.exists() && dir.isDirectory, "Fixture not found: ${dir.absolutePath}")

            val request = json.decodeFromString(RenderRequest.serializer(), File(dir, "input.json").readText())
            val actual = TemplateRenderer.render(request.template, request.data, request.options)

            val expectedFile = File(dir, "expected.html")
            if (System.getenv("UPDATE_FIXTURES") == "1" || !expectedFile.exists()) {
                expectedFile.writeText(actual)
                println("Fixture '$name': wrote expected.html (${actual.length} chars)")
                return
            }

            assertEquals(
                expectedFile.readText(),
                actual,
                "Fixture '$name': rendered HTML differs from expected.html (run UPDATE_FIXTURES=1 to refresh)",
            )
        }
    }

    @Test
    fun letter() = assertTemplateFixture("letter")

    @Test
    fun twoColumnOverride() = assertTemplateFixture("two-column-override")

    @Test
    fun externalFont() = assertTemplateFixture("external-font")

    @Test
    fun pageBackground() = assertTemplateFixture("page-background")

    @Test
    fun keyValueBasic() = assertTemplateFixture("key-value-basic")

    @Test
    fun keyValueRuntime() = assertTemplateFixture("key-value-runtime")
}
