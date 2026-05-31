package bambamboole.pdfua.template

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Locks the full output of [TemplateJsonSchema.current] against a checked-in JSON snapshot.
 * The snapshot file is `src/test/resources/template-schema-snapshot.json`.
 *
 * When the schema legitimately changes, regenerate the snapshot via the system property
 * `-Dupdate.snapshots=true`. The next run will then re-lock.
 */
class TemplateJsonSchemaSnapshotTest {
    private val prettyJson =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    @Test
    fun schemaMatchesSnapshot() {
        val rendered = prettyJson.encodeToString(TemplateJsonSchema.current()) + "\n"

        val url =
            TemplateJsonSchemaSnapshotTest::class.java.classLoader
                .getResource("template-schema-snapshot.json")
                ?: fail("Snapshot resource not found on classpath")

        val sourcePath = sourceSnapshotPath()
        if (System.getProperty("update.snapshots") == "true") {
            sourcePath.toFile().writeText(rendered)
            return
        }

        val expected = java.io.File(url.toURI()).readText()
        if (rendered != expected) {
            sourcePath.toFile().writeText(rendered)
            assertEquals(expected, rendered, "Schema diverged from snapshot; refreshed file written to $sourcePath")
        }
    }

    private fun sourceSnapshotPath(): java.nio.file.Path {
        val classpathUrl =
            TemplateJsonSchemaSnapshotTest::class.java.classLoader
                .getResource("template-schema-snapshot.json")
                ?: fail("Snapshot resource not on classpath")
        val classpathFile = java.io.File(classpathUrl.toURI())
        val projectRoot = classpathFile.absolutePath.substringBefore("/build/")
        return java.nio.file.Paths
            .get(projectRoot, "src/test/resources/template-schema-snapshot.json")
    }
}
