package com.sanzenkai.mangatracker.core.domain.model

enum class MonitoringInterval(val hours: Long) {
    ONE_HOUR(1),
    THREE_HOURS(3),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    TWENTY_FOUR_HOURS(24),
    ;

    companion object {
        val DEFAULT = ONE_HOUR

        fun fromHours(hours: Long): MonitoringInterval =
            values().firstOrNull { it.hours == hours } ?: DEFAULT
    }
}
