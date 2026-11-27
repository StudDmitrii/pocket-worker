package com.pocketworker.app.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Central runtime holder for the active application language and localized string catalog.
 *
 * - UI composables consume [LocalAppStrings] so switching language recomposes screens immediately.
 * - Non-UI components (ViewModels, Git engine, notifications, MCP confirmation summaries, error formatters)
 *   read [AppLocale.current] without requiring an Android Context.
 */
object AppLocale {
    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    @Volatile
    var resolvedLanguage: ResolvedLanguage = AppLanguage.SYSTEM.resolve()
        private set

    @Volatile
    var current: AppStrings = stringsForResolved(resolvedLanguage)
        private set

    fun setLanguage(language: AppLanguage, systemLocale: Locale = Locale.getDefault()) {
        val resolved = language.resolve(systemLocale)
        resolvedLanguage = resolved
        current = stringsForResolved(resolved)
        _language.value = language
    }

    fun stringsFor(language: AppLanguage, systemLocale: Locale = Locale.getDefault()): AppStrings {
        return stringsForResolved(language.resolve(systemLocale))
    }

    private fun stringsForResolved(resolved: ResolvedLanguage): AppStrings = when (resolved) {
        ResolvedLanguage.RU -> StringsRu
        ResolvedLanguage.EN -> StringsEn
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { AppLocale.current }
