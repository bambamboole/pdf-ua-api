plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Apply the Kotlin serialization plugin
    alias(libs.plugins.kotlinPluginSerialization)
}

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
    implementation(libs.ktorServerOpenApi)
    implementation(libs.ktorServerSwagger)
    implementation(libs.ktorServerRoutingOpenApi)

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

tasks.test {
    // Suppress warnings about restricted native access
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
