package com.pocketworker.app.core.i18n

import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.ui.ai.AiChatSession
import com.pocketworker.app.ui.ai.AiLocationContext
import com.pocketworker.app.ui.ai.MdCalloutType
import com.pocketworker.app.ui.ai.buildAiEmptyStatePrompt
import com.pocketworker.app.ui.ai.formatAiContextSubtitle
import com.pocketworker.app.ui.common.formatFileSize
import com.pocketworker.app.ui.settings.SettingsTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLocalizationTest {

    @After
    fun tearDown() {
        AppLocale.setLanguage(AppLanguage.SYSTEM)
    }

    @Test
    fun `system language resolves Russian for ru locales and English for all other locales`() {
        assertEquals(ResolvedLanguage.RU, AppLanguage.SYSTEM.resolve(Locale("ru", "RU")))
        assertEquals(ResolvedLanguage.RU, AppLanguage.SYSTEM.resolve(Locale("ru")))
        assertEquals(ResolvedLanguage.EN, AppLanguage.SYSTEM.resolve(Locale.US))
        assertEquals(ResolvedLanguage.EN, AppLanguage.SYSTEM.resolve(Locale.UK))
        assertEquals(ResolvedLanguage.EN, AppLanguage.SYSTEM.resolve(Locale.GERMANY))
        assertEquals(ResolvedLanguage.EN, AppLanguage.SYSTEM.resolve(Locale.JAPAN))
    }

    @Test
    fun `explicit language choice overrides system locale`() {
        assertEquals(ResolvedLanguage.RU, AppLanguage.RU.resolve(Locale.US))
        assertEquals(ResolvedLanguage.EN, AppLanguage.EN.resolve(Locale("ru", "RU")))
    }

    @Test
    fun `switching AppLocale updates UI helpers, providers, callouts, and chat titles`() {
        AppLocale.setLanguage(AppLanguage.EN, Locale("ru", "RU"))
        assertEquals(ResolvedLanguage.EN, AppLocale.resolvedLanguage)
        assertEquals("Local", AiProvider.LOCAL.displayName)
        assertEquals("What shall we work on in projects?", buildAiEmptyStatePrompt(AiLocationContext.AllProjects))
        assertEquals(
            "What shall we work on in project \"Shop\"?",
            buildAiEmptyStatePrompt(AiLocationContext.ProjectFolder(projectId = "1", projectName = "Shop"))
        )
        assertEquals("All projects", formatAiContextSubtitle(AiLocationContext.AllProjects))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("General", SettingsTab.GENERAL.localizedTitle(AppLocale.current))
        assertEquals("Warning", MdCalloutType.WARNING.title)
        assertEquals("New chat", AiChatSession(id = "1").localizedTitle(AppLocale.current))
        assertEquals("Permissions", AppLocale.current.settingsPermissionsTitle)
        assertEquals("Ask", AppLocale.current.settingsPermissionsAsk)
        assertEquals("Auto", AppLocale.current.settingsPermissionsAuto)

        AppLocale.setLanguage(AppLanguage.RU, Locale.US)
        assertEquals(ResolvedLanguage.RU, AppLocale.resolvedLanguage)
        assertEquals("Локальный", AiProvider.LOCAL.displayName)
        assertEquals("Над чем поработаем в проектах?", buildAiEmptyStatePrompt(AiLocationContext.AllProjects))
        assertEquals("Все проекты", formatAiContextSubtitle(AiLocationContext.AllProjects))
        assertEquals("512 Б", formatFileSize(512L))
        assertEquals("Общие", SettingsTab.GENERAL.localizedTitle(AppLocale.current))
        assertEquals("Внимание", MdCalloutType.WARNING.title)
        assertEquals("Новый чат", AiChatSession(id = "1").localizedTitle(AppLocale.current))
        assertEquals("Разрешения", AppLocale.current.settingsPermissionsTitle)
        assertEquals("Спрашивать", AppLocale.current.settingsPermissionsAsk)
        assertEquals("Авто", AppLocale.current.settingsPermissionsAuto)
    }

    @Test
    fun `all AppStrings val properties are non-blank in RU and EN and English has no untranslated Cyrillic`() {
        val cyrillicRegex = Regex("[А-Яа-яЁё]")
        val getters = AppStrings::class.java.methods.filter { method ->
            method.name.startsWith("get") &&
                method.parameterCount == 0 &&
                method.returnType == String::class.java
        }
        assertTrue("Expected extensive AppStrings catalog", getters.size >= 250)

        for (getter in getters) {
            val ruValue = getter.invoke(StringsRu) as String
            val enValue = getter.invoke(StringsEn) as String
            assertTrue("StringsRu.${getter.name} must not be blank", ruValue.isNotBlank())
            assertTrue("StringsEn.${getter.name} must not be blank", enValue.isNotBlank())
            if (getter.name == "getSettingsLanguageRussian") {
                assertEquals("Русский", enValue)
            } else {
                assertFalse(
                    "StringsEn.${getter.name} contains untranslated Cyrillic: '$enValue'",
                    cyrillicRegex.containsMatchIn(enValue)
                )
            }
        }
    }
}
