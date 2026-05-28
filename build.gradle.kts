import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

spotless {
    kotlin {
        target(
            "app/src/**/*.kt",
            "buildSrc/src/**/*.kt",
        )
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "buildSrc/*.gradle.kts",
            "buildSrc/src/**/*.gradle.kts",
        )
        ktlint(libs.versions.ktlint.get())
    }

    format("misc") {
        target(
            ".github/**/*.yml",
            ".gitignore",
            "*.md",
            "*.properties",
            "app/src/main/resources/**/*.conf",
            "app/src/main/resources/**/*.xml",
        )
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}

subprojects {
    plugins.withId("buildsrc.convention.kotlin-jvm") {
        apply(plugin = "dev.detekt")

        extensions.configure<DetektExtension>("detekt") {
            buildUponDefaultConfig.set(true)
            allRules.set(false)
            config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
            baseline.set(rootProject.layout.projectDirectory.file("config/detekt/baseline.xml"))
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget.set("24")

            reports {
                html.required.set(true)
                checkstyle.required.set(true)
                sarif.required.set(true)
                markdown.required.set(false)
            }
        }
    }
}
