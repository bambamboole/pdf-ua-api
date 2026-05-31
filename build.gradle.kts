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

        fun binarySchema() = mapOf("type" to "string", "format" to "binary")

        fun setBinaryResponse(
            path: String,
            method: String,
            contentTypes: List<String>,
        ) {
            @Suppress("UNCHECKED_CAST")
            val response =
                (paths[path]?.get(method)?.get("responses") as? MutableMap<String, Any?>)
                    ?.get("200") as? MutableMap<String, Any?> ?: return
            response["content"] = contentTypes.associateWith { mapOf("schema" to binarySchema()) }
        }

        setBinaryResponse("/convert", "post", listOf("application/pdf"))
        setBinaryResponse("/render/template", "post", listOf("application/pdf"))
        setBinaryResponse("/render", "post", listOf("image/png", "image/jpeg"))

        @Suppress("UNCHECKED_CAST")
        val schemaGet = paths["/schema"]?.get("get") as? MutableMap<String, Any?>
        if (schemaGet != null) {
            schemaGet["description"] =
                "Canonical JSON Schema (Draft 2020-12) for the template rendering payload. " +
                "The response is a JSON Schema document describing the Template type accepted " +
                "by /render/template, with builder metadata under x-pdfUa."
            @Suppress("UNCHECKED_CAST")
            val schemaResponse =
                (schemaGet["responses"] as? MutableMap<String, Any?>)?.get("200") as? MutableMap<String, Any?>
            schemaResponse?.put(
                "content",
                mapOf("application/json" to mapOf("schema" to mapOf("type" to "object"))),
            )
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
