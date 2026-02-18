import io.github.tabilzad.ktor.model.SecurityScheme

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Apply the Kotlin serialization plugin
    alias(libs.plugins.kotlinPluginSerialization)

    id("io.github.tabilzad.inspektor") version "0.10.0-alpha"
}

val appVersion = project.findProperty("app.version")?.toString() ?: "dev"

dependencies {
    // Ktor Server
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerMustache)
    implementation(libs.ktorSerializationJson)
    implementation(libs.ktorServerSwagger)

    // Kotlinx Serialization
    implementation(libs.kotlinxSerialization)

    // OpenHTMLToPDF
    implementation(libs.openhtmltopdfCore)
    implementation(libs.openhtmltopdfPdfbox)

    // HTML Parser
    implementation(libs.jsoup)

    // Logging
    implementation(libs.logbackClassic)

    // PDF Validation
    implementation(libs.verapdfValidation)
    implementation(libs.verapdfCore)

    // OpenAPI annotations
    implementation("io.github.tabilzad.inspektor:annotations:0.10.0-alpha")

    // Testing
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.kotlinTest)
}

application {
    // Define the Fully Qualified Name for the application main class
    mainClass = "bambamboole.pdf.api.ApplicationKt"
}

// Create fat JAR with all dependencies for Docker
tasks.jar {
    manifest {
        attributes["Main-Class"] = "bambamboole.pdf.api.ApplicationKt"
    }

    // Include all dependencies in the JAR
    duplicatesStrategy = DuplicatesStrategy.WARN
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        // Exclude signature files to avoid conflicts
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

tasks.test {
    // Suppress warnings about restricted native access
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
                "bearerAuth" to SecurityScheme(
                    type = "http",
                    scheme = "bearer"
                )
            }
        }
    }
    pluginOptions {
        format = "yaml"
    }
}
