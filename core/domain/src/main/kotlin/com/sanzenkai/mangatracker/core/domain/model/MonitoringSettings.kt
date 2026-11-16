package com.sanzenkai.mangatracker.core.domain.model

data class MonitoringSettings(
    val mangaId: String,
    val isEnabled: Boolean,
    val lastAttemptAtEpochMillis: Long?,
    val lastSuccessAtEpochMillis: Long?,
    val lastOutcome: String?,
)
