package com.pocketworker.app.core.storage

import android.content.Context
import android.content.SharedPreferences
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
    val label: String,
    val subtitle: String
)

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val apiKeyLabel: String,
    val apiKeyPlaceholder: String,
    val models: List<AiModelOption>
) {
    ANTHROPIC(
        displayName = "Claude",
        defaultModel = "claude-sonnet-5-5",
        defaultBaseUrl = "https://api.anthropic.com",
        apiKeyLabel = "API-ключ Claude (Android Keystore)",
        apiKeyPlaceholder = "sk-ant-...",
        models = listOf(
            AiModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5", "Оптимальный баланс скорости и качества кода"),
            AiModelOption("claude-opus-5-5", "Claude Opus 5.5", "Флагман для долгой агентской разработки"),
            AiModelOption("claude-fable-5-2", "Claude Fable 5.2", "Глубокое рассуждение и сложные задачи"),
            AiModelOption("claude-haiku-5-5", "Claude Haiku 5.5", "Сверхбыстрая и экономичная модель")
        )
    ),
    GEMINI(
        displayName = "Gemini",
        defaultModel = "gemini-4-neon",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        apiKeyLabel = "API-ключ Google AI Studio (Keystore)",
        apiKeyPlaceholder = "AIza...",
        models = listOf(
            AiModelOption("gemini-4-neon", "Gemini 4 Neon", "Быстрая модель Gemini 4 для кода и инструментов"),
            AiModelOption("gemini-4-carbon", "Gemini 4 Carbon", "Флагман Gemini 4 для сложной архитектуры"),
            AiModelOption("gemini-4-hydrogen", "Gemini 4 Hydrogen", "Лёгкая и экономичная модель Gemini 4"),
            AiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", "Быстрая универсальная модель"),
            AiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Максимальная скорость и низкая цена"),
            AiModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Pro-модель с расширенным вызовом инструментов")
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultModel = "google/gemini-4-neon",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        apiKeyLabel = "API-ключ OpenRouter (Keystore)",
        apiKeyPlaceholder = "sk-or-v1-...",
        models = listOf(
            AiModelOption("google/gemini-4-neon", "Gemini 4 Neon", "Быстрая модель Gemini 4 для кода и инструментов"),
            AiModelOption("google/gemini-4-carbon", "Gemini 4 Carbon", "Флагман Gemini 4 для сложной архитектуры"),
            AiModelOption("google/gemini-4-hydrogen", "Gemini 4 Hydrogen", "Лёгкая и экономичная модель Gemini 4"),
            AiModelOption("google/gemini-3.5-flash", "Gemini 3.5 Flash", "Быстрая универсальная модель"),
            AiModelOption("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Максимальная скорость и низкая цена"),
            AiModelOption("google/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Pro-модель с расширенным вызовом инструментов"),
            AiModelOption("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash Preview (free)", "Бесплатная модель через OpenRouter")
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-6.2-sol",
        defaultBaseUrl = "https://api.openai.com/v1",
        apiKeyLabel = "API-ключ OpenAI (Android Keystore)",
        apiKeyPlaceholder = "sk-...",
        models = listOf(
            AiModelOption("gpt-6.2-sol", "GPT-6.2 Sol", "Сбалансированная модель для кода и агентов"),
            AiModelOption("gpt-6.2-astra", "GPT-6.2 Astra", "Флагман OpenAI для сложных задач и анализа"),
            AiModelOption("gpt-6.1-terra", "GPT-6.1 Terra", "Быстрая модель для агентских задач"),
            AiModelOption("gpt-6-luna", "GPT-6 Luna", "Экономичная модель для частых запросов"),
            AiModelOption("gpt-5.5", "GPT-5.5", "Надёжная модель с контекстом 1M+"),
            AiModelOption("gpt-5.4", "GPT-5.4", "Проверенная модель для работы с инструментами")
        )
    ),
    LOCAL(
        displayName = "Локальный",
        defaultModel = "gemma-4-E2B-it",
        defaultBaseUrl = "",
        apiKeyLabel = "",
        apiKeyPlaceholder = "",
        models = listOf(
            AiModelOption(
                id = "gemma-4-E2B-it",
                label = "Gemma 4 E2B (~2.58 ГБ)",
                subtitle = "Офлайн-модель для смартфонов (6–8+ ГБ ОЗУ)"
            ),
            AiModelOption(
                id = "gemma-4-E4B-it",
                label = "Gemma 4 E4B (~3.65 ГБ)",
                subtitle = "Умнее для кода и инструментов (8–12+ ГБ ОЗУ)"
            )
        )
    );

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
    val animatedBackgroundEnabled: Boolean = true,
    val aiSettings: AiAgentSettings = AiAgentSettings(),
    val hasSeenOnboardingPdf: Boolean = false
)

interface AppPreferencesStore {
    val preferences: StateFlow<AppPreferences>
    fun setThemeMode(mode: AppThemeMode)
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
