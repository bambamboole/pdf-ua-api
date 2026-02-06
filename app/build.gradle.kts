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
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))

    // Ktor Server
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorSerializationJson)

    // Kotlinx Serialization
    implementation(libs.kotlinxSerialization)

    // OpenHTMLToPDF
    implementation(libs.openhtmltopdfCore)
    implementation(libs.openhtmltopdfPdfbox)

    // Logging
    implementation(libs.logbackClassic)

    // Testing
    testImplementation(libs.ktorServerTestHost)
}

application {
    // Define the Fully Qualified Name for the application main class
    mainClass = "org.example.app.ApplicationKt"
}
