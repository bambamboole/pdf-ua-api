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
val skipTemplateBuilderWebUiBuild = providers.gradleProperty("skipTemplateBuilderWebUiBuild")
    .map(String::toBoolean)
    .orElse(false)
val templateBuilderWebUiDir = layout.projectDirectory.dir("src/webui/template-builder")
val templateBuilderWebUiOutput = layout.buildDirectory.dir("generated-resources/webui/template-builder")
val openApiOutputDir = layout.buildDirectory.dir("resources/main/openapi")

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
    implementation(libs.ktorServerForwardedHeader)
    implementation(libs.ktorServerRateLimit)

    // Kotlinx Serialization
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlinxSchemaJson)

    // OpenHTMLToPDF
    implementation(libs.openhtmltopdfCore)
    implementation(libs.openhtmltopdfPdfbox)
    implementation(libs.openhtmltopdfJava2d)

    // HTML Parser
    implementation(libs.jsoup)

    // Logging
    implementation(libs.logbackClassic)
    implementation(libs.logstashLogbackEncoder)

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
    mainClass = "bambamboole.pdfua.ApplicationKt"
}

// Create fat JAR with all dependencies for Docker
tasks.jar {
    manifest {
        attributes["Main-Class"] = "bambamboole.pdfua.ApplicationKt"
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

val installTemplateBuilderWebUi by tasks.registering(Exec::class) {
    workingDir = templateBuilderWebUiDir.asFile
    commandLine("npm", "ci")
    inputs.file(templateBuilderWebUiDir.file("package.json"))
    inputs.file(templateBuilderWebUiDir.file("package-lock.json"))
    outputs.dir(templateBuilderWebUiDir.dir("node_modules"))
}

val buildTemplateBuilderWebUi by tasks.registering(Exec::class) {
    dependsOn(installTemplateBuilderWebUi)
    workingDir = templateBuilderWebUiDir.asFile
    commandLine("npm", "run", "build")
    environment("PDF_UA_TEMPLATE_BUILDER_OUT_DIR", templateBuilderWebUiOutput.get().asFile.absolutePath)
    inputs.file(templateBuilderWebUiDir.file("package.json"))
    inputs.file(templateBuilderWebUiDir.file("package-lock.json"))
    inputs.file(templateBuilderWebUiDir.file("tsconfig.json"))
    inputs.file(templateBuilderWebUiDir.file("vite.config.ts"))
    inputs.file(templateBuilderWebUiDir.file("index.html"))
    inputs.dir(templateBuilderWebUiDir.dir("src"))
    outputs.dir(templateBuilderWebUiOutput)
}

sourceSets.main {
    resources.srcDir(generateVersionProperties.map { layout.buildDirectory.dir("generated-resources") })
}

tasks.processResources {
    if (!skipTemplateBuilderWebUiBuild.get()) {
        dependsOn(buildTemplateBuilderWebUi)
    }
}

val prepareOpenApiOutputDirectory by tasks.registering {
    outputs.dir(openApiOutputDir)
    doLast {
        outputs.files.singleFile.mkdirs()
    }
}

tasks.compileKotlin {
    dependsOn(prepareOpenApiOutputDirectory)
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
