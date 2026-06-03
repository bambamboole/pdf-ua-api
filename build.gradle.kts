import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)

    application
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

    // Barcode / QR generation
    implementation(libs.okapibarcode)

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

    // OpenAPI generation (runtime, assembled from the routing tree)
    implementation(libs.ktorServerOpenApi)
    implementation(libs.ktorOpenApiSchema)

    // MCP
    implementation(libs.mcpKotlinServer)

    // Testing
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mcpKotlinClient)
    testImplementation(libs.mcpKotlinTesting)
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

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx4g")
}

// Regenerates docs/openapi/openapi.json from the routing tree. The regular `test` task verifies the
// committed spec is current; run this and commit the result after changing route `describe {}` metadata.
tasks.register<Test>("updateOpenApi") {
    group = "openapi"
    description = "Regenerates docs/openapi/openapi.json from the routing tree."
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("bambamboole.pdfua.http.OpenApiSpecGeneratorTest") }
    systemProperty("updateOpenApi", "true")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx4g")
    outputs.upToDateWhen { false }
}
