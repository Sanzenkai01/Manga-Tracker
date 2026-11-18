package com.sanzenkai.mangatracker.ui

import android.app.Application
import com.sanzenkai.mangatracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LanguageResourcesTest {
    @Test
    @Config(qualifiers = "en")
    fun usesEnglishResourcesForAnEnglishSystemLocale() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("Settings", context.getString(R.string.settings_title))
        assertEquals(
            "Manga Tracker opening animation",
            context.getString(R.string.opening_animation_content_description),
        )
        assertEquals("Add a manga", context.getString(R.string.site_discovery_action))
        assertEquals("Add a manga", context.getString(R.string.site_discovery_title))
        assertEquals("Search", context.getString(R.string.site_analyze_button))
        assertEquals("Manga or chapter link", context.getString(R.string.site_url_label))
        assertEquals(
            "Chapter 12 · 1 unread",
            context.getString(
                R.string.library_card_summary,
                "Chapter 12",
                context.resources.getQuantityString(R.plurals.library_card_unread_count, 1, 1),
            ),
        )
        assertEquals(
            "This site loads chapters in the browser. Paste a chapter link to add it.",
            context.getString(R.string.site_dynamic_series_page_hint),
        )
        assertEquals(
            "Automatic updates may not work for this site.",
            context.getString(R.string.site_incomplete_list_registration_note),
        )
        assertEquals(
            "Follows the device language when available (English fallback).",
            context.getString(R.string.settings_language_follow_system),
        )
        assertEquals(
            "The app checks using the selected interval when internet and enough battery are available. Android may delay checks.",
            context.getString(R.string.settings_monitoring_limitation),
        )
        assertFalse(context.getString(R.string.settings_monitoring_limitation).contains("WorkManager"))
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun usesBrazilianPortugueseResourcesForThatSystemLocale() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("Configurações", context.getString(R.string.settings_title))
        assertEquals(
            "Animação de abertura do Manga Tracker",
            context.getString(R.string.opening_animation_content_description),
        )
        assertEquals("Adicionar um mangá", context.getString(R.string.site_discovery_action))
        assertEquals("Adicionar mangá", context.getString(R.string.site_discovery_title))
        assertEquals("Buscar", context.getString(R.string.site_analyze_button))
        assertEquals("Link do mangá ou capítulo", context.getString(R.string.site_url_label))
        assertEquals(
            "Capítulo 12 · 1 não lido",
            context.getString(
                R.string.library_card_summary,
                "Capítulo 12",
                context.resources.getQuantityString(R.plurals.library_card_unread_count, 1, 1),
            ),
        )
        assertEquals(
            "Este site carrega os capítulos no navegador. Cole o link de um capítulo para adicioná-lo.",
            context.getString(R.string.site_dynamic_series_page_hint),
        )
        assertEquals(
            "Segue o idioma do dispositivo quando disponível (inglês como alternativa).",
            context.getString(R.string.settings_language_follow_system),
        )
        assertEquals(
            "As atualizações automáticas podem não funcionar neste site.",
            context.getString(R.string.site_incomplete_list_registration_note),
        )
        assertEquals(
            "O app faz a busca no intervalo escolhido quando há internet e bateria suficiente. O Android pode adiar as buscas.",
            context.getString(R.string.settings_monitoring_limitation),
        )
        assertEquals(
            "Busca automática ativada",
            context.getString(R.string.monitoring_enabled),
        )
        assertFalse(context.getString(R.string.settings_monitoring_limitation).contains("WorkManager"))
    }
}
