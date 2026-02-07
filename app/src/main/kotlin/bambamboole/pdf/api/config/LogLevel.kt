package bambamboole.pdf.api.config

import org.slf4j.event.Level

enum class LogLevel(val slf4jLevel: Level) {
    DEBUG(Level.DEBUG),
    INFO(Level.INFO),
    WARN(Level.WARN),
    ERROR(Level.ERROR);

    companion object {
        fun fromString(value: String): LogLevel =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: INFO
    }
}
