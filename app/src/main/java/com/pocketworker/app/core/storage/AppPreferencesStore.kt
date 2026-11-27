package com.pocketworker.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.pocketworker.app.core.i18n.AppLanguage
import com.pocketworker.app.core.i18n.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AiModelOption(
    val id: String,
    private val defaultLabel: String,
    private val defaultSubtitle: String
) {
    val label: String
        get() = AppLocale.current.modelLabel(id, defaultLabel)

    val subtitle: String
        get() = AppLocale.current.modelSubtitle(id, defaultSubtitle)
}

enum class AiProvider(
    val defaultModel: String,
    val defaultBaseUrl: String,
    val apiKeyPlaceholder: String,
    val models: List<AiModelOption>
) {
    ANTHROPIC(
        defaultModel = "claude-sonnet-5-5",
        defaultBaseUrl = "https://api.anthropic.com",
        apiKeyPlaceholder = "sk-ant-...",
        models = listOf(
            AiModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5", ""),
            AiModelOption("claude-opus-5-5", "Claude Opus 5.5", ""),
            AiModelOption("claude-fable-5-2", "Claude Fable 5.2", ""),
            AiModelOption("claude-haiku-5-5", "Claude Haiku 5.5", "")
        )
    ),
    GEMINI(
        defaultModel = "gemini-4-neon",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        apiKeyPlaceholder = "AIza...",
        models = listOf(
            AiModelOption("gemini-4-neon", "Gemini 4 Neon", ""),
            AiModelOption("gemini-4-carbon", "Gemini 4 Carbon", ""),
            AiModelOption("gemini-4-hydrogen", "Gemini 4 Hydrogen", ""),
            AiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", ""),
            AiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", ""),
            AiModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "")
        )
    ),
    OPENROUTER(
        defaultModel = "particle-ai/particle-1:free",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        apiKeyPlaceholder = "sk-or-v1-...",
        models = listOf(
            AiModelOption("particle-ai/particle-1:free", "Particle-1 (free)", ""),
            AiModelOption("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B (free)", ""),
            AiModelOption("google/gemma-4-31b-it:free", "Gemma 4 31B (free)", ""),
            AiModelOption("inclusionai/ling-3.2-flash:free", "Ling 3.2 Flash (free)", ""),
            AiModelOption("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash Preview (free)", "")
        )
    ),
    OPENAI(
        defaultModel = "gpt-6.2-sol",
        defaultBaseUrl = "https://api.openai.com/v1",
        apiKeyPlaceholder = "sk-...",
        models = listOf(
            AiModelOption("gpt-6.2-sol", "GPT-6.2 Sol", ""),
            AiModelOption("gpt-6.2-astra", "GPT-6.2 Astra", ""),
            AiModelOption("gpt-6.1-terra", "GPT-6.1 Terra", ""),
            AiModelOption("gpt-6-luna", "GPT-6 Luna", ""),
            AiModelOption("gpt-5.5", "GPT-5.5", ""),
            AiModelOption("gpt-5.4", "GPT-5.4", "")
        )
    ),
    LOCAL(
        defaultModel = "gemma-4-E2B-it",
        defaultBaseUrl = "",
        apiKeyPlaceholder = "",
        models = listOf(
            AiModelOption(
                id = "gemma-4-E2B-it",
                defaultLabel = "Gemma 4 E2B",
                defaultSubtitle = ""
            ),
            AiModelOption(
                id = "gemma-4-E4B-it",
                defaultLabel = "Gemma 4 E4B",
                defaultSubtitle = ""
            )
        )
    );

    val displayName: String
        get() = AppLocale.current.providerDisplayName(this)

    val apiKeyLabel: String
        get() = AppLocale.current.providerApiKeyLabel(this)

    val requiresApiKey: Boolean
        get() = this != LOCAL

    fun resolveModelOrDefault(modelId: String?): String {
        val clean = modelId?.trim().orEmpty()
        return models.firstOrNull { it.id == clean }?.id ?: defaultModel
    }

    fun findModelOption(modelId: String?): AiModelOption {
        val resolvedId = resolveModelOrDefault(modelId)
        return models.first { it.id == resolvedId }
    }
}

data class AiAgentSettings(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val modelName: String = AiProvider.ANTHROPIC.defaultModel,
    val systemPrompt: String = "",
    val shareWorkspaceContext: Boolean = true,
    val confirmFileEdits: Boolean = true,
    val proxyEnabled: Boolean = false,
    val localUseGpu: Boolean = false
)

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val animatedBackgroundEnabled: Boolean = true,
    val aiSettings: AiAgentSettings = AiAgentSettings(),
    val hasSeenOnboardingPdf: Boolean = false
)

interface AppPreferencesStore {
    val preferences: StateFlow<AppPreferences>
    fun setThemeMode(mode: AppThemeMode)
    fun setLanguage(language: AppLanguage)
    fun setAnimatedBackgroundEnabled(enabled: Boolean)
    fun updateAiSettings(settings: AiAgentSettings)
    fun setOnboardingPdfSeen(seen: Boolean)
}

class InMemoryAppPreferencesStore(
    initial: AppPreferences = AppPreferences()
) : AppPreferencesStore {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override fun setLanguage(language: AppLanguage) {
        _preferences.value = _preferences.value.copy(language = language)
    }

    override fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(animatedBackgroundEnabled = enabled)
    }

    override fun updateAiSettings(settings: AiAgentSettings) {
        _preferences.value = _preferences.value.copy(aiSettings = settings)
    }

    override fun setOnboardingPdfSeen(seen: Boolean) {
        _preferences.value = _preferences.value.copy(hasSeenOnboardingPdf = seen)
    }
}

class SharedPreferencesAppPreferencesStore(context: Context) : AppPreferencesStore {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _preferences = MutableStateFlow(readCurrent())
    override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
        _preferences.value = _preferences.value.copy(language = language)
    }

    override fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATED_BG, enabled).apply()
        _preferences.value = _preferences.value.copy(animatedBackgroundEnabled = enabled)
    }

    override fun updateAiSettings(settings: AiAgentSettings) {
        val resolvedModel = settings.provider.resolveModelOrDefault(settings.modelName)
        val normalized = settings.copy(modelName = resolvedModel)
        prefs.edit()
            .putString(KEY_AI_PROVIDER, normalized.provider.name)
            .putString(KEY_AI_MODEL, normalized.modelName)
            .putString(KEY_AI_SYSTEM_PROMPT, normalized.systemPrompt)
            .putBoolean(KEY_AI_SHARE_CONTEXT, normalized.shareWorkspaceContext)
            .putBoolean(KEY_AI_CONFIRM_EDITS, normalized.confirmFileEdits)
            .putBoolean(KEY_AI_PROXY_ENABLED, normalized.proxyEnabled)
            .putBoolean(KEY_AI_LOCAL_USE_GPU, normalized.localUseGpu)
            .apply()
        _preferences.value = _preferences.value.copy(aiSettings = normalized)
    }

    override fun setOnboardingPdfSeen(seen: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_PDF_SEEN, seen).apply()
        _preferences.value = _preferences.value.copy(hasSeenOnboardingPdf = seen)
    }

    private fun readCurrent(): AppPreferences {
        val rawTheme = prefs.getString(KEY_THEME_MODE, null)
        val theme = rawTheme?.let {
            runCatching { AppThemeMode.valueOf(it) }.getOrNull()
        } ?: AppThemeMode.SYSTEM
        val rawLanguage = prefs.getString(KEY_APP_LANGUAGE, null)
        val language = rawLanguage?.let {
            runCatching { AppLanguage.valueOf(it) }.getOrNull()
        } ?: AppLanguage.SYSTEM
        val animatedBg = prefs.getBoolean(KEY_ANIMATED_BG, true)
        val onboardingSeen = prefs.getBoolean(KEY_ONBOARDING_PDF_SEEN, false)

        val rawProvider = prefs.getString(KEY_AI_PROVIDER, null)
        val rawModel = prefs.getString(KEY_AI_MODEL, null)
        var provider = rawProvider?.let {
            runCatching { AiProvider.valueOf(it) }.getOrNull()
        } ?: AiProvider.ANTHROPIC
        // Migrate legacy OpenRouter selection that previously lived under AiProvider.GEMINI (slash-prefixed OpenRouter model IDs)
        if (provider == AiProvider.GEMINI && rawModel?.contains('/') == true) {
            provider = AiProvider.OPENROUTER
            prefs.edit().putString(KEY_AI_PROVIDER, AiProvider.OPENROUTER.name).apply()
        }
        val model = provider.resolveModelOrDefault(rawModel)
        val systemPrompt = prefs.getString(KEY_AI_SYSTEM_PROMPT, null).orEmpty()
        val shareContext = prefs.getBoolean(KEY_AI_SHARE_CONTEXT, true)
        val confirmEdits = prefs.getBoolean(KEY_AI_CONFIRM_EDITS, true)
        val proxyEnabled = prefs.getBoolean(KEY_AI_PROXY_ENABLED, false)
        val localUseGpu = prefs.getBoolean(KEY_AI_LOCAL_USE_GPU, false)

        return AppPreferences(
            themeMode = theme,
            language = language,
            animatedBackgroundEnabled = animatedBg,
            aiSettings = AiAgentSettings(
                provider = provider,
                modelName = model,
                systemPrompt = systemPrompt,
                shareWorkspaceContext = shareContext,
                confirmFileEdits = confirmEdits,
                proxyEnabled = proxyEnabled,
                localUseGpu = localUseGpu
            ),
            hasSeenOnboardingPdf = onboardingSeen
        )
    }

    companion object {
        private const val PREFS_NAME = "pocket_worker_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_ANIMATED_BG = "animated_background_enabled"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_SYSTEM_PROMPT = "ai_system_prompt"
        private const val KEY_AI_SHARE_CONTEXT = "ai_share_context"
        private const val KEY_AI_CONFIRM_EDITS = "ai_confirm_edits"
        private const val KEY_AI_PROXY_ENABLED = "ai_proxy_enabled"
        private const val KEY_AI_LOCAL_USE_GPU = "ai_local_use_gpu"
        private const val KEY_ONBOARDING_PDF_SEEN = "onboarding_pdf_seen"
    }
}
