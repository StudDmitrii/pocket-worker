package com.pocketworker.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pocketworker.app.core.ai.AiAgentNotificationConstants
import com.pocketworker.app.core.storage.AppThemeMode
import com.pocketworker.app.ui.navigation.AppNavHost
import com.pocketworker.app.ui.theme.PocketWorkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PocketWorkerApplication).container
        handleChatNotificationIntent(intent, container)

        setContent {
            val prefs by container.preferencesStore.preferences.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (prefs.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            PocketWorkerTheme(darkTheme = isDarkTheme) {
                AppNavHost(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as? PocketWorkerApplication)?.container ?: return
        handleChatNotificationIntent(intent, container)
    }

    override fun onResume() {
        super.onResume()
        (application as? PocketWorkerApplication)
            ?.container
            ?.agentBackgroundCoordinator
            ?.onAppMovedToForeground()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isChangingConfigurations && !isFinishing) {
            (application as? PocketWorkerApplication)
                ?.container
                ?.agentBackgroundCoordinator
                ?.onAppMovedToBackground()
        }
    }

    private fun handleChatNotificationIntent(intent: Intent?, container: AppContainer) {
        if (intent == null) return
        if (!intent.getBooleanExtra(AiAgentNotificationConstants.EXTRA_OPEN_AI_CHAT, false)) return
        val chatId = intent.getStringExtra(AiAgentNotificationConstants.EXTRA_AI_CHAT_ID)
        if (!chatId.isNullOrBlank()) {
            container.requestOpenAiChat(chatId)
        }
        // Prevent re-triggering on configuration change
        intent.removeExtra(AiAgentNotificationConstants.EXTRA_OPEN_AI_CHAT)
        intent.removeExtra(AiAgentNotificationConstants.EXTRA_AI_CHAT_ID)
    }
}
