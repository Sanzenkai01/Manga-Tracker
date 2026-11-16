package com.sanzenkai.mangatracker.core.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedMessageTest {
    @Test
    fun choosesMessageFromTheCurrentSystemLanguage() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Settings could not be loaded.", localizedMessage("Settings could not be loaded.", "Não foi possível carregar as configurações."))

            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            assertEquals("Não foi possível carregar as configurações.", localizedMessage("Settings could not be loaded.", "Não foi possível carregar as configurações."))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
