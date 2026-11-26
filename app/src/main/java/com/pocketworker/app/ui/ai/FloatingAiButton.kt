package com.pocketworker.app.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DockSide {
    NONE,
    LEFT,
    RIGHT
}

@Stable
class FloatingAiButtonState(
    initialDockSide: DockSide = DockSide.NONE,
    initialXFraction: Float = 1f,
    initialYFraction: Float = 0.62f
) {
    var dockSide by mutableStateOf(initialDockSide)
    var xFraction by mutableFloatStateOf(initialXFraction.coerceIn(0f, 1f))
    var yFraction by mutableFloatStateOf(initialYFraction.coerceIn(0f, 1f))
    var lastCenterPx by mutableStateOf<Offset?>(null)

    companion object {
        val Saver: Saver<FloatingAiButtonState, Any> = listSaver(
            save = { listOf(it.dockSide.name, it.xFraction, it.yFraction) },
            restore = { saved ->
                val side = runCatching { DockSide.valueOf(saved[0] as String) }.getOrDefault(DockSide.NONE)
                val xf = (saved[1] as? Float) ?: 1f
                val yf = (saved[2] as? Float) ?: 0.62f
                FloatingAiButtonState(side, xf, yf)
            }
        )
    }
}

typealias FloatingCircleState = FloatingAiButtonState

@Composable
fun rememberFloatingCircleState(
    initialDockSide: DockSide = DockSide.NONE,
    initialXFraction: Float = 1f,
    initialYFraction: Float = 0.62f
): FloatingCircleState {
    return rememberSaveable(saver = FloatingAiButtonState.Saver) {
        FloatingAiButtonState(
            initialDockSide = initialDockSide,
            initialXFraction = initialXFraction,
            initialYFraction = initialYFraction
        )
    }
}

@Composable
fun rememberFloatingAiButtonState(): FloatingAiButtonState = rememberFloatingCircleState()

internal data class FloatingButtonBounds(
    val containerWidthPx: Float,
    val containerHeightPx: Float,
    val buttonSizePx: Float,
    val leftInsetPx: Float,
    val topInsetPx: Float,
    val rightInsetPx: Float,
    val bottomInsetPx: Float,
    val safeMarginPx: Float,
    val snapThresholdPx: Float
) {
    val isValid: Boolean
        get() = containerWidthPx > buttonSizePx && containerHeightPx > buttonSizePx

    val minY: Float
        get() = topInsetPx + safeMarginPx

    val maxY: Float
        get() = (containerHeightPx - bottomInsetPx - buttonSizePx - safeMarginPx).coerceAtLeast(minY)

    val minSafeX: Float
        get() = leftInsetPx + safeMarginPx

    val maxSafeX: Float
        get() = (containerWidthPx - rightInsetPx - buttonSizePx - safeMarginPx).coerceAtLeast(minSafeX)

    val dockedLeftX: Float
        get() = leftInsetPx - buttonSizePx / 2f

    val dockedRightX: Float
        get() = containerWidthPx - rightInsetPx - buttonSizePx / 2f

    fun xFromFraction(fraction: Float): Float {
        return minSafeX + fraction.coerceIn(0f, 1f) * (maxSafeX - minSafeX)
    }

    fun yFromFraction(fraction: Float): Float {
        return minY + fraction.coerceIn(0f, 1f) * (maxY - minY)
    }

    fun xToFraction(xPx: Float): Float {
        val span = maxSafeX - minSafeX
        return if (span <= 0.5f) 1f else ((xPx - minSafeX) / span).coerceIn(0f, 1f)
    }

    fun yToFraction(yPx: Float): Float {
        val span = maxY - minY
        return if (span <= 0.5f) 0.5f else ((yPx - minY) / span).coerceIn(0f, 1f)
    }
}

internal data class DropTarget(
    val xPx: Float,
    val yPx: Float,
    val dockSide: DockSide
)

internal fun detectSnapEdge(xPx: Float, bounds: FloatingButtonBounds): DockSide {
    val nearLeft = xPx <= bounds.leftInsetPx + bounds.snapThresholdPx
    val nearRight = xPx + bounds.buttonSizePx >= bounds.containerWidthPx - bounds.rightInsetPx - bounds.snapThresholdPx
    return when {
        nearLeft -> DockSide.LEFT
        nearRight -> DockSide.RIGHT
        else -> DockSide.NONE
    }
}

internal fun resolveDropTarget(
    releasedXPx: Float,
    releasedYPx: Float,
    bounds: FloatingButtonBounds
): DropTarget {
    val clampedY = releasedYPx.coerceIn(bounds.minY, bounds.maxY)
    val snapSide = detectSnapEdge(releasedXPx, bounds)
    return when (snapSide) {
        DockSide.LEFT -> DropTarget(bounds.dockedLeftX, clampedY, DockSide.LEFT)
        DockSide.RIGHT -> DropTarget(bounds.dockedRightX, clampedY, DockSide.RIGHT)
        DockSide.NONE -> DropTarget(releasedXPx.coerceIn(bounds.minSafeX, bounds.maxSafeX), clampedY, DockSide.NONE)
    }
}

internal fun resolveUndockX(dockSide: DockSide, bounds: FloatingButtonBounds): Float {
    return when (dockSide) {
        DockSide.LEFT -> bounds.minSafeX
        DockSide.RIGHT -> bounds.maxSafeX
        DockSide.NONE -> bounds.maxSafeX
    }
}

private fun dockSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun positionSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

private val AiWaveGradientColors = listOf(
    Color(0xFF0284C7), // Deep Sky
    Color(0xFF38BDF8), // Soft Cyan
    Color(0xFF6366F1), // Indigo
    Color(0xFFA855F7), // Soft Violet
    Color(0xFFEC4899)  // Rose Magenta
)

/**
 * Full-screen transparent overlay that hosts the draggable, edge-dockable iridescent "AI" button.
 */
@Composable
fun FloatingAiButtonOverlay(
    state: FloatingAiButtonState,
    onOpenAiChat: (centerInContainerPx: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingCircleOverlay(
        state = state,
        dockedDescription = "Выдвинуть кнопку AI",
        undockedDescription = "Открыть чат с AI",
        onClick = onOpenAiChat,
        modifier = modifier
    ) { contentAlpha, pressScale, glowIntensity ->
        IridescentAiOrb(
            size = 60.dp,
            label = "AI",
            textSize = 18.sp,
            glowIntensity = glowIntensity,
            contentAlpha = contentAlpha,
            scale = pressScale
        )
    }
}

@Composable
fun FloatingFolderButtonOverlay(
    state: FloatingCircleState,
    onReturnToFolders: (centerInContainerPx: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingCircleOverlay(
        state = state,
        dockedDescription = "Выдвинуть кнопку возврата",
        undockedDescription = "Вернуться в папки",
        onClick = onReturnToFolders,
        modifier = modifier
    ) { contentAlpha, pressScale, _ ->
        CalmFolderOrb(
            size = 60.dp,
            contentAlpha = contentAlpha,
            scale = pressScale
        )
    }
}

@Composable
private fun FloatingCircleOverlay(
    state: FloatingCircleState,
    dockedDescription: String,
    undockedDescription: String,
    onClick: (centerInContainerPx: Offset) -> Unit,
    modifier: Modifier = Modifier,
    orbContent: @Composable (contentAlpha: Float, pressScale: Float, glowIntensity: Float) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val coroutineScope = rememberCoroutineScope()

    val buttonSize = 60.dp
    val buttonSizePx = with(density) { buttonSize.toPx() }
    val safeMarginPx = with(density) { 16.dp.toPx() }
    val snapThresholdPx = with(density) { 10.dp.toPx() }

    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    val leftInsetPx = insets.getLeft(density, layoutDirection).toFloat()
    val topInsetPx = insets.getTop(density).toFloat()
    val rightInsetPx = insets.getRight(density, layoutDirection).toFloat()
    val bottomInsetPx = insets.getBottom(density).toFloat()

    val bounds = remember(
        containerWidthPx,
        containerHeightPx,
        buttonSizePx,
        leftInsetPx,
        topInsetPx,
        rightInsetPx,
        bottomInsetPx,
        safeMarginPx,
        snapThresholdPx
    ) {
        FloatingButtonBounds(
            containerWidthPx = containerWidthPx.toFloat(),
            containerHeightPx = containerHeightPx.toFloat(),
            buttonSizePx = buttonSizePx,
            leftInsetPx = leftInsetPx,
            topInsetPx = topInsetPx,
            rightInsetPx = rightInsetPx,
            bottomInsetPx = bottomInsetPx,
            safeMarginPx = safeMarginPx,
            snapThresholdPx = snapThresholdPx
        )
    }

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragXPx by remember { mutableFloatStateOf(0f) }
    var dragYPx by remember { mutableFloatStateOf(0f) }
    var previewDockSide by remember { mutableStateOf(DockSide.NONE) }

    val currentBounds by rememberUpdatedState(bounds)
    val currentState by rememberUpdatedState(state)
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(
        bounds,
        state.dockSide,
        state.xFraction,
        state.yFraction,
        isDragging
    ) {
        if (!bounds.isValid || isDragging) return@LaunchedEffect

        val targetX = when (state.dockSide) {
            DockSide.LEFT -> bounds.dockedLeftX
            DockSide.RIGHT -> bounds.dockedRightX
            DockSide.NONE -> bounds.xFromFraction(state.xFraction)
        }
        val targetY = bounds.yFromFraction(state.yFraction)
        state.lastCenterPx = Offset(
            x = targetX + buttonSizePx / 2f,
            y = targetY + buttonSizePx / 2f
        )

        if (!initialized) {
            animX.snapTo(targetX)
            animY.snapTo(targetY)
            initialized = true
        } else {
            launch { animX.animateTo(targetX, dockSpring()) }
            launch { animY.animateTo(targetY, positionSpring()) }
        }
    }

    val isEffectivelyDocked = (state.dockSide != DockSide.NONE && !isDragging) ||
        (isDragging && previewDockSide != DockSide.NONE)

    val buttonAlpha by animateFloatAsState(
        targetValue = when {
            state.dockSide != DockSide.NONE && !isDragging -> 0.44f
            isDragging && previewDockSide != DockSide.NONE -> 0.68f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 220),
        label = "floating_button_alpha"
    )
    val glowIntensity by animateFloatAsState(
        targetValue = if (isEffectivelyDocked) 0.14f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "floating_button_glow"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "floating_button_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidthPx = size.width
                containerHeightPx = size.height
            }
    ) {
        if (bounds.isValid && initialized) {
            val renderX = if (isDragging) dragXPx else animX.value
            val renderY = if (isDragging) dragYPx else animY.value

            Box(
                modifier = Modifier
                    .offset { IntOffset(renderX.roundToInt(), renderY.roundToInt()) }
                    .size(buttonSize)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (state.dockSide != DockSide.NONE) {
                            dockedDescription
                        } else {
                            undockedDescription
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startDock = currentState.dockSide
                            val startBoxX = animX.value
                            val startBoxY = animY.value
                            val startFingerContainer = Offset(
                                startBoxX + down.position.x,
                                startBoxY + down.position.y
                            )
                            isPressed = true
                            var dragging = false
                            var cancelled = false
                            val touchSlop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    break
                                }

                                val curBoxX = if (dragging) dragXPx else animX.value
                                val curBoxY = if (dragging) dragYPx else animY.value
                                val curFingerContainer = Offset(
                                    curBoxX + change.position.x,
                                    curBoxY + change.position.y
                                )
                                val totalDelta = curFingerContainer - startFingerContainer

                                if (!dragging && totalDelta.getDistance() > touchSlop) {
                                    if (startDock != DockSide.NONE) {
                                        val pullingInward = when (startDock) {
                                            DockSide.LEFT -> totalDelta.x > 0f && abs(totalDelta.x) > abs(totalDelta.y)
                                            DockSide.RIGHT -> totalDelta.x < 0f && abs(totalDelta.x) > abs(totalDelta.y)
                                            DockSide.NONE -> false
                                        }
                                        if (!pullingInward) {
                                            cancelled = true
                                            break
                                        }
                                        currentState.dockSide = DockSide.NONE
                                    }
                                    dragging = true
                                    dragXPx = startBoxX
                                    dragYPx = startBoxY
                                    isDragging = true
                                    isPressed = false
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        animX.stop()
                                        animY.stop()
                                    }
                                }

                                if (dragging) {
                                    change.consume()
                                    val b = currentBounds
                                    if (b.isValid) {
                                        dragXPx = (startBoxX + totalDelta.x).coerceIn(b.dockedLeftX, b.dockedRightX)
                                        dragYPx = (startBoxY + totalDelta.y).coerceIn(b.minY, b.maxY)
                                        previewDockSide = detectSnapEdge(dragXPx, b)
                                    }
                                }
                            }

                            isPressed = false
                            previewDockSide = DockSide.NONE

                            if (cancelled) {
                                // Ignore vertical edge drags so underlying lists remain scrollable
                            } else if (dragging) {
                                val b = currentBounds
                                val finalX = dragXPx
                                val finalY = dragYPx
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    animX.snapTo(finalX)
                                    animY.snapTo(finalY)
                                }
                                isDragging = false

                                if (b.isValid) {
                                    val drop = resolveDropTarget(finalX, finalY, b)
                                    currentState.dockSide = drop.dockSide
                                    currentState.yFraction = b.yToFraction(drop.yPx)
                                    if (drop.dockSide == DockSide.NONE) {
                                        currentState.xFraction = b.xToFraction(drop.xPx)
                                    }
                                    coroutineScope.launch { animX.animateTo(drop.xPx, dockSpring()) }
                                    coroutineScope.launch { animY.animateTo(drop.yPx, positionSpring()) }
                                }
                            } else {
                                if (startDock != DockSide.NONE) {
                                    val b = currentBounds
                                    currentState.dockSide = DockSide.NONE
                                    if (b.isValid) {
                                        val undockX = resolveUndockX(startDock, b)
                                        currentState.xFraction = b.xToFraction(undockX)
                                    }
                                } else {
                                    currentOnClick(
                                        Offset(
                                            x = animX.value + buttonSizePx / 2f,
                                            y = animY.value + buttonSizePx / 2f
                                        )
                                    )
                                }
                            }
                        }
                    }
            ) {
                orbContent(buttonAlpha, pressScale, glowIntensity)
            }
        }
    }
}

/**
 * Calm tonal circular Folder button without shimmer/waves, matching the AI button geometry.
 */
@Composable
fun CalmFolderOrb(
    size: Dp,
    contentAlpha: Float = 1f,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Circular "AI" orb with a slow, calm edge-to-edge linear wave gradient and clean label.
 */
@Composable
fun IridescentAiOrb(
    size: Dp,
    label: String = "AI",
    textSize: TextUnit = 18.sp,
    glowIntensity: Float = 1f,
    contentAlpha: Float = 1f,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_wave_orb")
    val primaryWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_primary_wave"
    )
    val crestWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_crest_wave"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
            }
            .drawBehind {
                if (glowIntensity > 0.01f) {
                    val auraRadius = this.size.minDimension * 0.68f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.24f * glowIntensity),
                                Color(0xFF38BDF8).copy(alpha = 0.10f * glowIntensity),
                                Color.Transparent
                            ),
                            center = center,
                            radius = auraRadius
                        ),
                        radius = auraRadius,
                        center = center
                    )
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .drawBehind {
                    val d = this.size.minDimension
                    val radius = d / 2f

                    // Primary smooth edge-to-edge linear wave (periodic with 2 * waveVector via TileMode.Mirror)
                    val primaryVector = Offset(d * 2.2f, d * 0.45f)
                    val primaryShift = primaryVector * (primaryWaveProgress * 2f)
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = AiWaveGradientColors,
                            start = primaryShift,
                            end = primaryShift + primaryVector,
                            tileMode = TileMode.Mirror
                        ),
                        radius = radius,
                        center = center
                    )

                    // Subtle secondary translucent wave flowing softly across at a gentle counter-angle
                    val crestVector = Offset(d * 1.9f, -d * 0.5f)
                    val crestShift = crestVector * (crestWaveProgress * 2f)
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            start = crestShift,
                            end = crestShift + crestVector,
                            tileMode = TileMode.Mirror
                        ),
                        radius = radius,
                        center = center
                    )
                }
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        shadow = Shadow(
                            color = Color(0x88090D16),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 5f
                        )
                    )
                )
            }
        }
    }
}
