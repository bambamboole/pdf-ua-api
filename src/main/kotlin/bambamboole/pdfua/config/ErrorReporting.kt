package bambamboole.pdfua.config

import io.sentry.Sentry

fun initErrorReporting(
    config: AppConfig,
    version: String,
) {
    val sentry = config.sentry ?: return
    Sentry.init { options ->
        options.dsn = sentry.dsn
        options.environment = sentry.environment
        options.release = sentry.release ?: "pdf-ua-api@$version"
    }
}
