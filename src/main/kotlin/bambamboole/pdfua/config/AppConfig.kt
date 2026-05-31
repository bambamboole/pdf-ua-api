package bambamboole.pdfua.config

import io.ktor.server.application.*
import io.ktor.server.config.*

data class JwtConfig(
    val issuer: String,
    val jwksUrl: String,
    val audience: String?,
)

data class AppConfig(
    val apiKey: String?,
    val jwt: JwtConfig?,
    val pdfProducer: String,
    val maxRequestSizeBytes: Long,
    val logLevel: LogLevel,
    val logFormat: String,
    val assetTimeoutMs: Long,
    val assetMaxSizeBytes: Long,
    val assetAllowedDomains: Set<String>,
    val trustPrivateHosts: Boolean,
    val uploadEnabled: Boolean,
    val uploadTimeoutMs: Long,
    val uploadAllowedDomains: Set<String>,
    val rateLimitEnabled: Boolean,
    val rateLimitPerIp: Int,
    val rateLimitGlobal: Int,
    val rateLimitWindowSeconds: Long,
    val rateLimitTrustForwardedFor: Boolean,
    val corsAllowedOrigins: Set<String>,
) {
    private object Defaults {
        const val PDF_PRODUCER = "pdf-ua-api.com"
        const val MAX_REQUEST_SIZE_BYTES: Long = 10L * 1024 * 1024
        const val LOG_FORMAT = "text"
        const val ASSET_TIMEOUT_MS: Long = 5_000
        const val ASSET_MAX_SIZE_BYTES: Long = 5L * 1024 * 1024
        const val TRUST_PRIVATE_HOSTS = true
        const val UPLOAD_ENABLED = true
        const val UPLOAD_TIMEOUT_MS: Long = 30_000
        const val RATE_LIMIT_ENABLED = true
        const val RATE_LIMIT_PER_IP = 20
        const val RATE_LIMIT_GLOBAL = 200
        const val RATE_LIMIT_WINDOW_SECONDS: Long = 60
        const val RATE_LIMIT_TRUST_FORWARDED_FOR = false
    }

    companion object {
        fun load(environment: ApplicationEnvironment): AppConfig {
            val config = environment.config
            return AppConfig(
                apiKey = config.optional("api.key"),
                jwt = parseJwt(config),
                pdfProducer = config.required("pdf.producer", Defaults.PDF_PRODUCER),
                maxRequestSizeBytes = config.long("pdf.maxRequestSize", Defaults.MAX_REQUEST_SIZE_BYTES),
                logLevel = config.logLevel("logging.level", LogLevel.INFO),
                logFormat = config.required("logging.format", Defaults.LOG_FORMAT),
                assetTimeoutMs = config.long("assets.timeout", Defaults.ASSET_TIMEOUT_MS),
                assetMaxSizeBytes = config.long("assets.maxSize", Defaults.ASSET_MAX_SIZE_BYTES),
                assetAllowedDomains = parseCsvSet(config.optional("assets.allowedDomains")),
                trustPrivateHosts = config.bool("trustPrivateHosts", Defaults.TRUST_PRIVATE_HOSTS),
                uploadEnabled = config.bool("upload.enabled", Defaults.UPLOAD_ENABLED),
                uploadTimeoutMs = config.long("upload.timeout", Defaults.UPLOAD_TIMEOUT_MS),
                uploadAllowedDomains = parseCsvSet(config.optional("upload.allowedDomains")),
                rateLimitEnabled = config.bool("rateLimit.enabled", Defaults.RATE_LIMIT_ENABLED),
                rateLimitPerIp = config.int("rateLimit.perIp", Defaults.RATE_LIMIT_PER_IP),
                rateLimitGlobal = config.int("rateLimit.global", Defaults.RATE_LIMIT_GLOBAL),
                rateLimitWindowSeconds = config.long("rateLimit.windowSeconds", Defaults.RATE_LIMIT_WINDOW_SECONDS),
                rateLimitTrustForwardedFor = config.bool("rateLimit.trustForwardedFor", Defaults.RATE_LIMIT_TRUST_FORWARDED_FOR),
                corsAllowedOrigins = parseCsvSet(config.optional("cors.allowedOrigins")),
            )
        }
    }
}

private fun ApplicationConfig.optional(key: String): String? = propertyOrNull(key)?.getString()?.takeIf { it.isNotBlank() }

private fun ApplicationConfig.required(
    key: String,
    default: String,
): String = optional(key) ?: default

private fun ApplicationConfig.bool(
    key: String,
    default: Boolean,
): Boolean = optional(key)?.toBoolean() ?: default

private fun ApplicationConfig.long(
    key: String,
    default: Long,
): Long = optional(key)?.toLong() ?: default

private fun ApplicationConfig.int(
    key: String,
    default: Int,
): Int = optional(key)?.toInt() ?: default

private fun ApplicationConfig.logLevel(
    key: String,
    default: LogLevel,
): LogLevel = optional(key)?.let { LogLevel.fromString(it) } ?: default

private fun parseCsvSet(raw: String?): Set<String> =
    raw
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()

private fun parseJwt(config: ApplicationConfig): JwtConfig? {
    val issuer = config.optional("jwt.issuer")
    val jwksUrl = config.optional("jwt.jwksUrl")
    val audience = config.optional("jwt.audience")
    return when {
        issuer != null && jwksUrl != null -> {
            JwtConfig(issuer = issuer, jwksUrl = jwksUrl, audience = audience)
        }

        issuer != null || jwksUrl != null -> {
            error(
                "JWT auth requires both JWT_ISSUER and JWT_JWKS_URL to be set; missing " +
                    if (issuer == null) "JWT_ISSUER" else "JWT_JWKS_URL",
            )
        }

        else -> {
            null
        }
    }
}
