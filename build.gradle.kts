plugins {
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
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
