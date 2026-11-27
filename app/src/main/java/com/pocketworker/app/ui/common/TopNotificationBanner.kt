package com.pocketworker.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TopNotificationItem(
    val id: Long,
    val message: String,
    val isError: Boolean = false,
    val durationMillis: Long = DEFAULT_TOP_NOTIFICATION_DURATION_MS
)

const val DEFAULT_TOP_NOTIFICATION_DURATION_MS = 3_600L

@Stable
class TopNotificationState {
    var current by mutableStateOf<TopNotificationItem?>(null)
        private set

    private var nextId = 1L

    fun show(
        message: String,
        isError: Boolean = false,
        durationMillis: Long = DEFAULT_TOP_NOTIFICATION_DURATION_MS
    ) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        current = TopNotificationItem(
            id = nextId++,
            message = clean,
            isError = isError,
            durationMillis = durationMillis.coerceAtLeast(800L)
        )
    }

    fun dismiss(id: Long? = null) {
        val active = current ?: return
        if (id == null || active.id == id) {
            current = null
        }
    }
}

@Composable
fun rememberTopNotificationState(): TopNotificationState = remember { TopNotificationState() }

/**
 * Top notification banner styled consistently with the app UI in a neutral gray palette:
 * - Slides downward from underneath the screen header (clipped to the top boundary).
 * - Can be swiped left or right to dismiss immediately.
 * - Displays a bottom countdown bar showing when it will auto-dismiss.
 * - Auto-dismisses by sliding smoothly upward back underneath the header.
 */
@Composable
fun TopNotificationHost(
    state: TopNotificationState,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val extraSlidePx = with(density) { 16.dp.toPx() }

    var displayedItem by remember { mutableStateOf<TopNotificationItem?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var bannerHeightPx by remember { mutableIntStateOf(0) }
    var bannerWidthPx by remember { mutableIntStateOf(0) }

    val verticalProgress = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val remainingFraction = remember { Animatable(1f) }

    val incoming = state.current

    // Handle new notifications or external clear
    LaunchedEffect(incoming?.id) {
        if (incoming != null) {
            displayedItem = incoming
            isDragging = false
            isDismissing = false
            offsetX.snapTo(0f)
            remainingFraction.snapTo(1f)
            verticalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        } else if (displayedItem != null && !isDismissing) {
            isDismissing = true
            verticalProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing)
            )
            displayedItem = null
            isDismissing = false
        }
    }

    // Auto-dismiss countdown timer (pauses while user is dragging horizontally)
    val activeItem = displayedItem
    LaunchedEffect(activeItem?.id, isDragging, isDismissing) {
        if (activeItem == null || isDragging || isDismissing) return@LaunchedEffect
        val remaining = remainingFraction.value.coerceIn(0f, 1f)
        if (remaining > 0f) {
            val remainingMs = (activeItem.durationMillis * remaining).toLong().coerceAtLeast(1L)
            try {
                remainingFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = remainingMs.toInt(), easing = LinearEasing)
                )
            } catch (ce: CancellationException) {
                throw ce
            }
        }
        if (!isDragging && !isDismissing && displayedItem?.id == activeItem.id && remainingFraction.value <= 0.001f) {
            isDismissing = true
            verticalProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            if (displayedItem?.id == activeItem.id) {
                state.dismiss(activeItem.id)
                displayedItem = null
                isDismissing = false
            }
        }
    }

    if (activeItem == null) return

    val cardBg = if (isDark) {
        Color(0xFF232934).copy(alpha = 0.97f)
    } else {
        Color(0xFFECEFF4).copy(alpha = 0.97f)
    }
    val cardBorder = if (isDark) {
        Color(0xFF3E4859)
    } else {
        Color(0xFFCBD5E1)
    }
    val progressTrack = if (isDark) {
        Color(0xFF94A3B8).copy(alpha = 0.18f)
    } else {
        Color(0xFF64748B).copy(alpha = 0.16f)
    }
    val progressFill = if (isDark) {
        Color(0xFF94A3B8).copy(alpha = 0.78f)
    } else {
        Color(0xFF64748B).copy(alpha = 0.72f)
    }
    val iconTint = if (activeItem.isError) {
        MaterialTheme.colorScheme.error
    } else if (isDark) {
        Color(0xFFCBD5E1)
    } else {
        Color(0xFF475569)
    }

    val draggableState = rememberDraggableState { delta ->
        if (!isDismissing) {
            scope.launch {
                offsetX.snapTo(offsetX.value + delta)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(10f)
            .clipToBounds()
    ) {
        val slideDistancePx = if (bannerHeightPx > 0) bannerHeightPx + extraSlidePx else 0f
        val swipeAlpha = if (bannerWidthPx > 0) {
            (1f - (abs(offsetX.value) / (bannerWidthPx * 0.92f))).coerceIn(0f, 1f)
        } else {
            1f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    bannerWidthPx = size.width
                    bannerHeightPx = size.height
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationY = -(1f - verticalProgress.value) * slideDistancePx
                    translationX = offsetX.value
                    alpha = if (bannerHeightPx == 0) 0f else swipeAlpha
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !isDismissing,
                    onDragStarted = { isDragging = true },
                    onDragStopped = { velocity ->
                        val width = bannerWidthPx.coerceAtLeast(1).toFloat()
                        val shouldDismiss = abs(offsetX.value) > width * 0.28f || abs(velocity) > 700f
                        if (shouldDismiss) {
                            val dir = when {
                                abs(offsetX.value) > 4f -> if (offsetX.value >= 0f) 1f else -1f
                                else -> if (velocity >= 0f) 1f else -1f
                            }
                            val targetItemId = activeItem.id
                            isDismissing = true
                            isDragging = false
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = dir * (width + 80f),
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                                verticalProgress.snapTo(0f)
                                offsetX.snapTo(0f)
                                if (displayedItem?.id == targetItemId) {
                                    state.dismiss(targetItemId)
                                    displayedItem = null
                                    isDismissing = false
                                }
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                isDragging = false
                            }
                        }
                    }
                )
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, cardBorder),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (activeItem.isError) {
                                Icons.Rounded.ErrorOutline
                            } else {
                                Icons.Rounded.CheckCircleOutline
                            },
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = activeItem.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Slim countdown bar indicating when the banner will auto-dismiss
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(progressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(remainingFraction.value.coerceIn(0f, 1f))
                                .background(progressFill)
                        )
                    }
                }
            }
        }
    }
}
