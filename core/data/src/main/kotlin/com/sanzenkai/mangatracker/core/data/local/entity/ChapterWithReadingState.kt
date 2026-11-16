package com.sanzenkai.mangatracker.core.data.local.entity

import androidx.room.Embedded

data class ChapterWithReadingState(
    @Embedded val chapter: ChapterEntity,
    val isRead: Boolean,
)
