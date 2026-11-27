package com.pocketworker.app.core.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pocketworker.app.MainActivity
import com.pocketworker.app.PocketWorkerApplication
import com.pocketworker.app.R
import com.pocketworker.app.core.i18n.AppLocale

data class ActiveAgentRun(
    val chatId: String,
    val chatTitle: String,
    val statusText: String
)

interface AiAgentNotificationPresenter {
    fun showOrUpdateOngoing(chatId: String, chatTitle: String, statusText: String)
    fun hideOngoing()
    fun showFinished(chatId: String, chatTitle: String, title: String, contentText: String)
    fun cancelFinished(chatId: String)
}

/**
 * Coordinates AI agent background execution status with Android notifications:
 * - While the app is in foreground, no ongoing progress notification is shown.
 * - When the app is minimized while an agent task is running, shows and live-updates
 *   an ongoing notification mirroring the loader status text.
 * - When the agent finishes while the app is minimized, posts "Агент закончил задачу"
 *   with a tap deep-link to that exact chat.
 * - When the user swipes the app away from Recents (task removed), cancels the active agent run.
 */
class AiAgentBackgroundCoordinator(
    private val presenter: AiAgentNotificationPresenter
) {
    private val lock = Any()

    private var isAppInForeground: Boolean = true
    private var activeRun: ActiveAgentRun? = null
    private var cancelCallback: (() -> Unit)? = null

    fun currentRun(): ActiveAgentRun? = synchronized(lock) { activeRun }

    fun isInForeground(): Boolean = synchronized(lock) { isAppInForeground }

    fun registerCancelCallback(callback: (() -> Unit)?) {
        synchronized(lock) {
            cancelCallback = callback
        }
    }

    fun onAppMovedToBackground() {
        val runToShow: ActiveAgentRun? = synchronized(lock) {
            if (!isAppInForeground) return
            isAppInForeground = false
            activeRun
        }
        if (runToShow != null) {
            presenter.showOrUpdateOngoing(
                chatId = runToShow.chatId,
                chatTitle = runToShow.chatTitle,
                statusText = runToShow.statusText
            )
        }
    }

    fun onAppMovedToForeground() {
        synchronized(lock) {
            if (isAppInForeground) return
            isAppInForeground = true
        }
        presenter.hideOngoing()
    }

    fun onAgentStarted(chatId: String, chatTitle: String, statusText: String) {
        val cleanStatus = statusText.ifBlank { AppLocale.current.aiThinking }
        val showNow: Boolean = synchronized(lock) {
            activeRun = ActiveAgentRun(chatId, chatTitle, cleanStatus)
            !isAppInForeground
        }
        presenter.cancelFinished(chatId)
        if (showNow) {
            presenter.showOrUpdateOngoing(chatId, chatTitle, cleanStatus)
        }
    }

    fun onAgentStatusChanged(chatId: String, chatTitle: String, statusText: String) {
        val cleanStatus = statusText.ifBlank { AppLocale.current.aiThinking }
        val showNow: Boolean = synchronized(lock) {
            val prev = activeRun
            if (prev != null && prev.chatId == chatId && prev.statusText == cleanStatus && prev.chatTitle == chatTitle) {
                return
            }
            activeRun = ActiveAgentRun(chatId, chatTitle, cleanStatus)
            !isAppInForeground
        }
        if (showNow) {
            presenter.showOrUpdateOngoing(chatId, chatTitle, cleanStatus)
        }
    }

    fun onAgentFinished(
        chatId: String,
        chatTitle: String,
        wasCancelled: Boolean,
        hadError: Boolean,
        previewText: String = ""
    ) {
        val shouldNotifyFinished: Boolean = synchronized(lock) {
            if (activeRun?.chatId == chatId) {
                activeRun = null
            }
            !isAppInForeground && !wasCancelled
        }
        presenter.hideOngoing()

        if (shouldNotifyFinished) {
            val s = AppLocale.current
            val title = if (hadError) {
                s.notifFinishedErrorTitle
            } else {
                s.notifFinishedSuccessTitle
            }
            val compactPreview = previewText.replace('\n', ' ').trim().take(120)
            val contentText = when {
                compactPreview.isNotBlank() && chatTitle.isNotBlank() -> "$chatTitle: $compactPreview"
                compactPreview.isNotBlank() -> compactPreview
                chatTitle.isNotBlank() -> s.notifOpenNamedChat(chatTitle)
                else -> s.notifOpenChat
            }
            presenter.showFinished(
                chatId = chatId,
                chatTitle = chatTitle,
                title = title,
                contentText = contentText
            )
        }
    }

    fun clearFinishedNotification(chatId: String) {
        presenter.cancelFinished(chatId)
    }

    fun onAppTaskRemoved() {
        val callback: (() -> Unit)? = synchronized(lock) {
            activeRun = null
            cancelCallback
        }
        presenter.hideOngoing()
        callback?.invoke()
    }
}

object AiAgentNotificationConstants {
    const val CHANNEL_PROGRESS_ID = "ai_agent_progress"
    const val CHANNEL_FINISHED_ID = "ai_agent_finished"

    const val ONGOING_NOTIFICATION_ID = 41001
    private const val FINISHED_NOTIFICATION_BASE_ID = 42000

    const val EXTRA_OPEN_AI_CHAT = "com.pocketworker.app.EXTRA_OPEN_AI_CHAT"
    const val EXTRA_AI_CHAT_ID = "com.pocketworker.app.EXTRA_AI_CHAT_ID"

    const val EXTRA_SERVICE_CHAT_ID = "service_chat_id"
    const val EXTRA_SERVICE_CHAT_TITLE = "service_chat_title"
    const val EXTRA_SERVICE_STATUS_TEXT = "service_status_text"

    fun finishedNotificationId(chatId: String): Int {
        return FINISHED_NOTIFICATION_BASE_ID + (chatId.hashCode() and 0x0FFF)
    }

    fun createOpenChatPendingIntent(context: Context, chatId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_AI_CHAT, true)
            putExtra(EXTRA_AI_CHAT_ID, chatId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val s = AppLocale.current

        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS_ID,
            s.notifProgressChannelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = s.notifProgressChannelDesc
            setShowBadge(false)
        }

        val finishedChannel = NotificationChannel(
            CHANNEL_FINISHED_ID,
            s.notifFinishedChannelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = s.notifFinishedChannelDesc
            enableVibration(true)
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(finishedChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildOngoingNotification(
        context: Context,
        chatId: String,
        chatTitle: String,
        statusText: String
    ): Notification {
        ensureChannels(context)
        val s = AppLocale.current
        val title = if (chatTitle.isNotBlank()) s.notifOngoingNamedTitle(chatTitle) else s.notifOngoingDefaultTitle
        val pendingIntent = createOpenChatPendingIntent(
            context = context,
            chatId = chatId,
            requestCode = ONGOING_NOTIFICATION_ID
        )
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_stat_ai_agent)
            .setContentTitle(title)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }
}

class AndroidAiAgentNotificationPresenter(
    private val appContext: Context
) : AiAgentNotificationPresenter {

    init {
        AiAgentNotificationConstants.ensureChannels(appContext)
    }

    override fun showOrUpdateOngoing(chatId: String, chatTitle: String, statusText: String) {
        val notification = AiAgentNotificationConstants.buildOngoingNotification(
            context = appContext,
            chatId = chatId,
            chatTitle = chatTitle,
            statusText = statusText
        )

        if (AiAgentForegroundService.isRunning) {
            notifySafely(AiAgentNotificationConstants.ONGOING_NOTIFICATION_ID, notification)
            return
        }

        val serviceIntent = Intent(appContext, AiAgentForegroundService::class.java).apply {
            putExtra(AiAgentNotificationConstants.EXTRA_SERVICE_CHAT_ID, chatId)
            putExtra(AiAgentNotificationConstants.EXTRA_SERVICE_CHAT_TITLE, chatTitle)
            putExtra(AiAgentNotificationConstants.EXTRA_SERVICE_STATUS_TEXT, statusText)
        }
        val startedFgs = runCatching {
            ContextCompat.startForegroundService(appContext, serviceIntent)
            true
        }.getOrDefault(false)

        if (!startedFgs) {
            // Fallback on Android 12–15 if system restricts FGS start (e.g., screen locked)
            notifySafely(AiAgentNotificationConstants.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    override fun hideOngoing() {
        runCatching {
            appContext.stopService(Intent(appContext, AiAgentForegroundService::class.java))
        }
        runCatching {
            NotificationManagerCompat.from(appContext)
                .cancel(AiAgentNotificationConstants.ONGOING_NOTIFICATION_ID)
        }
    }

    override fun showFinished(
        chatId: String,
        chatTitle: String,
        title: String,
        contentText: String
    ) {
        if (!AiAgentNotificationConstants.hasNotificationPermission(appContext)) return
        AiAgentNotificationConstants.ensureChannels(appContext)
        val notifId = AiAgentNotificationConstants.finishedNotificationId(chatId)
        val pendingIntent = AiAgentNotificationConstants.createOpenChatPendingIntent(
            context = appContext,
            chatId = chatId,
            requestCode = notifId
        )
        val notification = NotificationCompat.Builder(appContext, AiAgentNotificationConstants.CHANNEL_FINISHED_ID)
            .setSmallIcon(R.drawable.ic_stat_ai_agent)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .build()

        notifySafely(notifId, notification)
    }

    override fun cancelFinished(chatId: String) {
        runCatching {
            NotificationManagerCompat.from(appContext)
                .cancel(AiAgentNotificationConstants.finishedNotificationId(chatId))
        }
    }

    private fun notifySafely(id: Int, notification: Notification) {
        if (!AiAgentNotificationConstants.hasNotificationPermission(appContext)) return
        runCatching {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        }
    }
}

/**
 * Lightweight foreground service keeping the AI agent streaming connection alive while
 * the app is minimized, and cancelling the active agent immediately if the app task is swiped away.
 */
class AiAgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = (application as? PocketWorkerApplication)?.container?.agentBackgroundCoordinator
        val run = coordinator?.currentRun()
        val chatId = intent?.getStringExtra(AiAgentNotificationConstants.EXTRA_SERVICE_CHAT_ID)
            ?: run?.chatId.orEmpty()
        val chatTitle = intent?.getStringExtra(AiAgentNotificationConstants.EXTRA_SERVICE_CHAT_TITLE)
            ?: run?.chatTitle.orEmpty()
        val statusText = intent?.getStringExtra(AiAgentNotificationConstants.EXTRA_SERVICE_STATUS_TEXT)
            ?: run?.statusText
            ?: AppLocale.current.aiThinking

        val notification = AiAgentNotificationConstants.buildOngoingNotification(
            context = this,
            chatId = chatId,
            chatTitle = chatTitle,
            statusText = statusText
        )

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                AiAgentNotificationConstants.ONGOING_NOTIFICATION_ID,
                notification,
                fgsType
            )
            isRunning = true
        }.onFailure {
            stopSelf()
            return START_NOT_STICKY
        }

        // If user already returned to foreground or agent already completed while starting, stop immediately
        if (coordinator == null || coordinator.isInForeground() || coordinator.currentRun() == null) {
            isRunning = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val coordinator = (application as? PocketWorkerApplication)?.container?.agentBackgroundCoordinator
        coordinator?.onAppTaskRemoved()
        isRunning = false
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
