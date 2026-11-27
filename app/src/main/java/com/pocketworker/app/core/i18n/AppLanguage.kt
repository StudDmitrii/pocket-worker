package com.pocketworker.app.core.i18n

import java.util.Locale

enum class AppLanguage {
    SYSTEM,
    RU,
    EN
}

enum class ResolvedLanguage {
    RU,
    EN
}

fun AppLanguage.resolve(systemLocale: Locale = Locale.getDefault()): ResolvedLanguage = when (this) {
    AppLanguage.RU -> ResolvedLanguage.RU
    AppLanguage.EN -> ResolvedLanguage.EN
    AppLanguage.SYSTEM -> {
        val lang = systemLocale.language.lowercase(Locale.ROOT)
        if (lang == "ru") ResolvedLanguage.RU else ResolvedLanguage.EN
    }
}
