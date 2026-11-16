package com.sanzenkai.mangatracker.core.domain.model

data class SiteConfiguration(
    val normalizedHost: String,
    val parserId: String,
    val formatVersion: Int,
    val configJson: String,
    val updatedAtEpochMillis: Long,
    val validatedAtEpochMillis: Long?,
)
