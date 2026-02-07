package bambamboole.pdf.api.config

import io.ktor.server.application.*

data class AppConfig(
    val apiKey: String?,
    val webUIEnabled: Boolean,
    val pdfProducer: String,
    val maxRequestSizeBytes: Long,
    val logLevel: LogLevel
) {
    companion object {
        fun load(environment: ApplicationEnvironment): AppConfig {
            fun getOptional(key: String): String? =
                environment.config.propertyOrNull(key)?.getString()

            fun getRequired(key: String, default: String): String =
                getOptional(key) ?: default

            fun getBoolean(key: String, default: Boolean): Boolean =
                getOptional(key)?.toBoolean() ?: default

            fun getLong(key: String, default: Long): Long =
                getOptional(key)?.toLong() ?: default

            fun getLogLevel(key: String, default: LogLevel): LogLevel =
                getOptional(key)?.let { LogLevel.fromString(it) } ?: default

            return AppConfig(
                apiKey = getOptional("api.key"),
                webUIEnabled = getBoolean("ui.enabled", true),
                pdfProducer = getRequired("pdf.producer", "pdf-ua-api.com"),
                maxRequestSizeBytes = getLong("pdf.maxRequestSize", 10 * 1024 * 1024),
                logLevel = getLogLevel("logging.level", LogLevel.INFO)
            )
        }
    }

    val isAuthenticationEnabled: Boolean
        get() = apiKey != null
}
