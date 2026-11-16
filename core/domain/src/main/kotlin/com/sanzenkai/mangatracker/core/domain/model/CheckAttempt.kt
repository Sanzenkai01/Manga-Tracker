package com.sanzenkai.mangatracker.core.domain.model

data class CheckAttempt(
    val id: String,
    val mangaId: String,
    val trigger: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val outcome: String,
    val errorCode: String?,
)
