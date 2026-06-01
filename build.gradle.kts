import dev.detekt.gradle.Detekt
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.github.tabilzad.ktor.model.SecurityScheme
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)

    application

    alias(libs.plugins.inspektor)
}

kotlin {
    jvmToolchain(24)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    baseline =
        rootProject.layout.projectDirectory
            .file("config/detekt/baseline.xml")
            .asFile
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "24"
    reports {
        html.required = true
        checkstyle.required = true
        sarif.required = true
        markdown.required = false
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }

    format("misc") {
        target(
            ".github/**/*.yml",
            ".gitignore",
            "*.md",
            "*.properties",
            "src/main/resources/**/*.conf",
            "src/main/resources/**/*.xml",
        )
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}

val appVersion = project.findProperty("app.version")?.toString() ?: "dev"
val openApiOutputDir = layout.buildDirectory.dir("resources/main/openapi")

dependencies {
    // Ktor Server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.di)
    implementation(ktorLibs.server.config.yaml)

    // Kotlinx Serialization
    implementation(libs.kotlinxSerialization)

    // OpenHTMLToPDF
    implementation(libs.openhtmltopdfCore)
    implementation(libs.openhtmltopdfPdfbox)
    implementation(libs.openhtmltopdfJava2d)

    // HTML Parser
    implementation(libs.jsoup)

    // Hyphenation
    implementation(libs.hypherator)

    // ImageIO plugins
    implementation(libs.imageioWebp)

    // Logging
    implementation(libs.logbackClassic)
    implementation(libs.logstashLogbackEncoder)

    // PDF Validation
    implementation(libs.verapdfValidation)
    implementation(libs.verapdfCore)

    // OpenAPI annotations
    implementation(libs.inspektorAnnotations)

    // Testing
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlinTest)
}

application {
    mainClass = "bambamboole.pdfua.ApplicationKt"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "bambamboole.pdfua.ApplicationKt"
    }

    duplicatesStrategy = DuplicatesStrategy.WARN
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val generateVersionProperties by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated-resources/version.properties")
    val version = appVersion
    inputs.property("version", version)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$version\n")
        }
    }
}

sourceSets.main {
    resources.srcDir(generateVersionProperties.map { layout.buildDirectory.dir("generated-resources") })
}

val prepareOpenApiOutputDirectory by tasks.registering {
    outputs.dir(openApiOutputDir)
    doLast {
        outputs.files.singleFile.mkdirs()
    }
}

// Patches the Inspektor-generated OpenAPI spec to declare accurate content types for binary
// responses and to enrich the /schema endpoint description. Drop once
// https://github.com/tabilzad/inspektor/issues/72 ships.
val patchOpenApi by tasks.registering {
    dependsOn("compileKotlin")
    mustRunAfter("processResources")
    val openApiFile = openApiOutputDir.map { it.file("openapi.json") }
    val docsCopy = rootProject.layout.projectDirectory.file("docs/openapi/openapi.json")
    inputs.file(openApiFile)
    outputs.file(openApiFile)
    outputs.file(docsCopy)
    doLast {
        val file = openApiFile.get().asFile

        @Suppress("UNCHECKED_CAST")
        val spec = JsonSlurper().parseText(file.readText()) as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val paths = spec["paths"] as MutableMap<String, MutableMap<String, MutableMap<String, Any?>>>

        @Suppress("UNCHECKED_CAST")
        val schemas =
            (
                (spec["components"] as? MutableMap<String, Any?>)
                    ?.get("schemas") as? MutableMap<String, Any?>
            )
                ?: error("OpenAPI spec is missing components.schemas")

        fun binarySchema() = mapOf("type" to "string", "format" to "binary")

        val validationResponseSchema =
            mapOf("\$ref" to "#/components/schemas/bambamboole.pdfua.http.ValidationResponse")
        val renderRequestSchemaName = "bambamboole.pdfua.http.controller.RenderRequest"

        fun renderPdfJsonSchema() =
            mapOf(
                "type" to "object",
                "properties" to
                    mapOf(
                        "validation" to validationResponseSchema,
                        "pdf" to mapOf("type" to "string"),
                    ),
                "required" to listOf("validation", "pdf"),
            )

        fun renderRequestSchema() =
            mapOf(
                "type" to "object",
                "properties" to
                    mapOf(
                        "data" to
                            mapOf(
                                "type" to "object",
                                "description" to
                                    "Per-block content overrides, keyed by block id. Object for most blocks; " +
                                    "array of row objects for tables.",
                                "additionalProperties" to true,
                            ),
                        "options" to
                            mapOf(
                                "type" to "object",
                                "properties" to
                                    mapOf(
                                        "baseUrl" to mapOf("type" to "string"),
                                        "title" to mapOf("type" to "string"),
                                    ),
                            ),
                        "template" to
                            mapOf(
                                "type" to "object",
                                "description" to
                                    "Template document to render. See GET /schema for the canonical template schema.",
                            ),
                    ),
                "required" to listOf("template"),
            )

        fun operation(
            path: String,
            method: String,
        ): MutableMap<String, Any?> =
            paths[path]?.get(method)
                ?: error("OpenAPI spec is missing required operation $method $path")

        @Suppress("UNCHECKED_CAST")
        fun responses(
            path: String,
            method: String,
        ): MutableMap<String, Any?> =
            operation(path, method)["responses"] as? MutableMap<String, Any?>
                ?: error("OpenAPI spec is missing responses for $method $path")

        @Suppress("UNCHECKED_CAST")
        fun response(
            path: String,
            method: String,
            status: String,
        ): MutableMap<String, Any?> =
            responses(path, method)[status] as? MutableMap<String, Any?>
                ?: error("OpenAPI spec is missing $status response for $method $path")

        fun setBinaryResponse(
            path: String,
            method: String,
            contentTypes: List<String>,
        ) {
            response(path, method, "200")["content"] =
                contentTypes.associateWith { mapOf("schema" to binarySchema()) }
        }

        fun setRenderPdfResponse(
            path: String,
            method: String,
        ) {
            response(path, method, "200").apply {
                this["description"] = "PDF document or JSON validation response"
                this["content"] =
                    mapOf(
                        "application/pdf" to mapOf("schema" to binarySchema()),
                        "application/json" to mapOf("schema" to renderPdfJsonSchema()),
                    )
            }
            response(path, method, "400")["description"] = "Invalid request or JSON/upload conflict"
        }

        @Suppress("UNCHECKED_CAST")
        fun addUploadHeaderParameter(
            path: String,
            method: String,
        ) {
            val operation = operation(path, method)
            val parameters =
                operation.getOrPut("parameters") { mutableListOf<Any?>() } as MutableList<Any?>
            val uploadParameter =
                mapOf(
                    "name" to "X-Upload-Url",
                    "in" to "header",
                    "required" to false,
                    "description" to "Presigned PUT URL that receives the generated document.",
                    "schema" to mapOf("type" to "string"),
                )
            val existingIndex =
                parameters.indexOfFirst {
                    (it as? Map<*, *>)?.get("name") == "X-Upload-Url" &&
                        (it as? Map<*, *>)?.get("in") == "header"
                }
            if (existingIndex >= 0) {
                parameters[existingIndex] = uploadParameter
            } else {
                parameters.add(uploadParameter)
            }
        }

        fun addUploadResponse(
            path: String,
            method: String,
            description: String,
        ) {
            responses(path, method)["204"] = mapOf("description" to description)
            responses(path, method)["502"] =
                mapOf("description" to "Upload target rejected the request or was unreachable")
        }

        fun setUploadDocs(
            path: String,
            method: String,
            uploadDescription: String,
        ) {
            addUploadHeaderParameter(path, method)
            addUploadResponse(path, method, uploadDescription)
        }

        fun setRequestBody(
            path: String,
            method: String,
            schemaRef: String,
        ) {
            operation(path, method)["requestBody"] =
                mapOf(
                    "required" to true,
                    "content" to
                        mapOf(
                            "application/json" to
                                mapOf(
                                    "schema" to mapOf("\$ref" to "#/components/schemas/$schemaRef"),
                                ),
                        ),
                )
        }

        setRenderPdfResponse("/render/html", "post")
        setUploadDocs("/render/html", "post", "PDF uploaded successfully")
        setRenderPdfResponse("/render/url", "post")
        setUploadDocs("/render/url", "post", "PDF uploaded successfully")
        setRenderPdfResponse("/render/template", "post")
        schemas.putIfAbsent(renderRequestSchemaName, renderRequestSchema())
        setRequestBody("/render/template", "post", renderRequestSchemaName)
        setUploadDocs("/render/template", "post", "PDF uploaded successfully")
        setBinaryResponse("/render", "post", listOf("image/png", "image/jpeg"))
        response("/render", "post", "400")["description"] = "Invalid request or upload URL"
        setUploadDocs("/render", "post", "Image uploaded successfully")

        val schemaGet = operation("/schema", "get")
        schemaGet["description"] =
            "Canonical JSON Schema (Draft 2020-12) for the template rendering payload. " +
            "The response is a JSON Schema document describing the Template type accepted " +
            "by /render/template, with builder metadata under x-pdfUa."
        response("/schema", "get", "200").apply {
            this["content"] =
                mapOf("application/json" to mapOf("schema" to mapOf("type" to "object")))
        }

        val rendered = JsonOutput.prettyPrint(JsonOutput.toJson(spec))
        file.writeText(rendered)
        docsCopy.asFile.also { it.parentFile.mkdirs() }.writeText(rendered)
    }
}

tasks.compileKotlin {
    dependsOn(prepareOpenApiOutputDirectory)
    finalizedBy(patchOpenApi)
}

tasks.classes {
    dependsOn(patchOpenApi)
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx4g")
}

swagger {
    documentation {
        info {
            title = "PDF API"
            description = "HTML to PDF/A-3a conversion API with PDF/UA accessibility support and veraPDF validation"
            version = appVersion
        }
        servers = listOf("http://localhost:8080")
        security {
            schemes {
                "bearerAuth" to
                    SecurityScheme(
                        type = "http",
                        scheme = "bearer",
                    )
            }
        }
    }
    pluginOptions {
        format = "json"
    }
}
