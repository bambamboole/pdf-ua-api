package bambamboole.pdfua.config

import io.ktor.server.application.*

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
    companion object {
        fun load(environment: ApplicationEnvironment): AppConfig {
            fun getOptional(key: String): String? =
                environment.config
                    .propertyOrNull(key)
                    ?.getString()
                    ?.takeIf { it.isNotBlank() }

            fun getRequired(
                key: String,
                default: String,
            ): String = getOptional(key) ?: default

            fun getBoolean(
                key: String,
                default: Boolean,
            ): Boolean = getOptional(key)?.toBoolean() ?: default

            fun getLong(
                key: String,
                default: Long,
            ): Long = getOptional(key)?.toLong() ?: default

            fun getInt(
                key: String,
                default: Int,
            ): Int = getOptional(key)?.toInt() ?: default

            fun getLogLevel(
                key: String,
                default: LogLevel,
            ): LogLevel = getOptional(key)?.let { LogLevel.fromString(it) } ?: default

            val jwtIssuer = getOptional("jwt.issuer")
            val jwtJwksUrl = getOptional("jwt.jwksUrl")
            val jwtAudience = getOptional("jwt.audience")
            val jwt =
                when {
                    jwtIssuer != null && jwtJwksUrl != null -> {
                        JwtConfig(issuer = jwtIssuer, jwksUrl = jwtJwksUrl, audience = jwtAudience)
                    }

                    jwtIssuer != null || jwtJwksUrl != null -> {
                        error(
                            "JWT auth requires both JWT_ISSUER and JWT_JWKS_URL to be set; missing " +
                                if (jwtIssuer == null) "JWT_ISSUER" else "JWT_JWKS_URL",
                        )
                    }

                    else -> {
                        null
                    }
                }

            return AppConfig(
                apiKey = getOptional("api.key"),
                jwt = jwt,
                pdfProducer = getRequired("pdf.producer", "pdf-ua-api.com"),
                maxRequestSizeBytes = getLong("pdf.maxRequestSize", 10 * 1024 * 1024),
                logLevel = getLogLevel("logging.level", LogLevel.INFO),
                logFormat = getRequired("logging.format", "text"),
                assetTimeoutMs = getLong("assets.timeout", 5000),
                assetMaxSizeBytes = getLong("assets.maxSize", 5 * 1024 * 1024),
                assetAllowedDomains =
                    getOptional("assets.allowedDomains")
                        ?.split(",")
                        ?.map { it.trim().lowercase() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: emptySet(),
                uploadEnabled = getBoolean("upload.enabled", true),
                uploadTimeoutMs = getLong("upload.timeout", 30_000),
                uploadAllowedDomains =
                    getOptional("upload.allowedDomains")
                        ?.split(",")
                        ?.map { it.trim().lowercase() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: emptySet(),
                rateLimitEnabled = getBoolean("rateLimit.enabled", true),
                rateLimitPerIp = getInt("rateLimit.perIp", 20),
                rateLimitGlobal = getInt("rateLimit.global", 200),
                rateLimitWindowSeconds = getLong("rateLimit.windowSeconds", 60),
                rateLimitTrustForwardedFor = getBoolean("rateLimit.trustForwardedFor", false),
                corsAllowedOrigins =
                    getOptional("cors.allowedOrigins")
                        ?.split(",")
                        ?.map { it.trim().lowercase() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: emptySet(),
            )
        }
    }
}
