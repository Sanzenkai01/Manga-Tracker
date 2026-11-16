package com.sanzenkai.mangatracker.core.localization

import java.util.Locale

fun localizedMessage(
    english: String,
    portugueseBrazilian: String,
): String =
    if (Locale.getDefault().language.equals("pt", ignoreCase = true)) {
        portugueseBrazilian
    } else {
        english
    }
