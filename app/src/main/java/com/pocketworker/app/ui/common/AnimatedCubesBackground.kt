package com.pocketworker.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CubeSpec(
    val xFraction: Float,
    val startYFraction: Float,
    val sizeDp: Float,
    val yCycles: Int,
    val swayCycles: Int,
    val swayDp: Float,
    val rotCyclesX: Int,
    val rotCyclesY: Int,
    val baseAx: Float,
    val baseAy: Float,
    val phaseOffset: Float,
    val tintIndex: Int,
    val alphaScale: Float
)

private val FloatingCubeSpecs = listOf(
    CubeSpec(0.14f, 0.85f, 34f, 1, 2, 10f, 1, 2, 0.4f, 0.5f, 0.0f, 0, 0.90f),
    CubeSpec(0.82f, 0.72f, 48f, 1, 2, 12f, 2, 1, 0.7f, 0.2f, 0.2f, 1, 0.75f),
    CubeSpec(0.38f, 0.52f, 26f, 2, 3, 8f, 2, 2, 0.2f, 0.9f, 0.4f, 2, 1.00f),
    CubeSpec(0.68f, 0.34f, 40f, 1, 2, 11f, 1, 1, 0.9f, 0.4f, 0.6f, 0, 0.80f),
    CubeSpec(0.22f, 0.18f, 44f, 1, 2, 9f, 1, 2, 0.5f, 0.8f, 0.8f, 1, 0.70f),
    CubeSpec(0.88f, 0.12f, 28f, 2, 2, 7f, 2, 1, 0.3f, 0.6f, 0.1f, 2, 0.95f),
    CubeSpec(0.52f, 0.95f, 36f, 1, 3, 10f, 1, 2, 0.6f, 0.3f, 0.5f, 0, 0.85f)
)

// 8 vertices of unit cube [-1, 1]^3
private val UnitCubeX = floatArrayOf(-1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f)
private val UnitCubeY = floatArrayOf(-1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f)
private val UnitCubeZ = floatArrayOf(-1f, -1f, -1f, -1f, 1f, 1f, 1f, 1f)

// 6 faces ordered CCW when viewed from outside (+Z toward viewer, +Y down)
private val CubeFaces = arrayOf(
    intArrayOf(4, 5, 6, 7), // Front
    intArrayOf(1, 0, 3, 2), // Back
    intArrayOf(0, 1, 5, 4), // Top
    intArrayOf(3, 7, 6, 2), // Bottom
    intArrayOf(5, 1, 2, 6), // Right
    intArrayOf(0, 4, 7, 3)  // Left
)

private val FaceShadeMultipliers = floatArrayOf(1.0f, 0.9f, 1.15f, 0.65f, 0.82f, 0.72f)

fun backdropCardContainerColor(isDark: Boolean): Color {
    return if (isDark) Color(0xFF131D31).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f)
}

fun backdropCardBorderColor(isDark: Boolean): Color {
    return if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1).copy(alpha = 0.55f)
}

/**
 * Renders a soft multi-stop theme-aware gradient backdrop with optional low-power,
 * non-distracting translucent 3D cubes drawn purely in the Canvas draw phase.
 */
@Composable
fun WorkspaceBackdrop(
    animatedCubesEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    val phaseState: State<Float> = if (animatedCubesEnabled) {
        val transition = rememberInfiniteTransition(label = "workspace_cubes")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 48_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cubes_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val gradientColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF070D19),
                Color(0xFF0F172A),
                Color(0xFF0B1F2A)
            )
        } else {
            listOf(
                Color(0xFFEEF6FF),
                Color(0xFFF6F4FF),
                Color(0xFFECFEFF)
            )
        }
    }

    val cubeTints = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF38BDF8),
                Color(0xFF818CF8),
                Color(0xFF2DD4BF)
            )
        } else {
            listOf(
                Color(0xFF0369A1),
                Color(0xFF6366F1),
                Color(0xFF0F766E)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                val reusablePath = Path()
                val projectedX = FloatArray(8)
                val projectedY = FloatArray(8)
                val edgeStroke = Stroke(width = 1.dp.toPx())
                val baseFillAlpha = if (isDark) 0.068f else 0.052f
                val baseEdgeAlpha = if (isDark) 0.115f else 0.085f

                onDrawBehind {
                    drawRect(brush = bgBrush)

                    if (animatedCubesEnabled) {
                        val phase = phaseState.value
                        val w = size.width
                        val h = size.height
                        val travelHeight = h * 1.28f

                        for (cube in FloatingCubeSpecs) {
                            val rawY = (cube.startYFraction - phase * cube.yCycles) % 1.0f
                            val wrappedY = if (rawY < 0f) rawY + 1.0f else rawY
                            val cy = wrappedY * travelHeight - h * 0.14f
                            val swayAngle = (phase * cube.swayCycles + cube.phaseOffset) * (2f * PI.toFloat())
                            val cx = cube.xFraction * w + sin(swayAngle) * cube.swayDp.dp.toPx()

                            val ax = cube.baseAx + phase * cube.rotCyclesX * (2f * PI.toFloat())
                            val ay = cube.baseAy + phase * cube.rotCyclesY * (2f * PI.toFloat())
                            val halfRadius = cube.sizeDp.dp.toPx() * 0.5f
                            val tint = cubeTints[cube.tintIndex]

                            drawFacetedCube(
                                cx = cx,
                                cy = cy,
                                halfRadius = halfRadius,
                                ax = ax,
                                ay = ay,
                                tint = tint,
                                fillAlpha = baseFillAlpha * cube.alphaScale,
                                edgeAlpha = baseEdgeAlpha * cube.alphaScale,
                                edgeStroke = edgeStroke,
                                path = reusablePath,
                                projX = projectedX,
                                projY = projectedY
                            )
                        }
                    }
                }
            }
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}

private fun DrawScope.drawFacetedCube(
    cx: Float,
    cy: Float,
    halfRadius: Float,
    ax: Float,
    ay: Float,
    tint: Color,
    fillAlpha: Float,
    edgeAlpha: Float,
    edgeStroke: Stroke,
    path: Path,
    projX: FloatArray,
    projY: FloatArray
) {
    val cosX = cos(ax)
    val sinX = sin(ax)
    val cosY = cos(ay)
    val sinY = sin(ay)

    for (i in 0 until 8) {
        val x0 = UnitCubeX[i]
        val y0 = UnitCubeY[i]
        val z0 = UnitCubeZ[i]

        // Rotate around X then Y
        val y1 = y0 * cosX - z0 * sinX
        val z1 = y0 * sinX + z0 * cosX
        val x2 = x0 * cosY + z1 * sinY

        projX[i] = cx + x2 * halfRadius
        projY[i] = cy + y1 * halfRadius
    }

    for (f in 0 until 6) {
        val face = CubeFaces[f]
        val i0 = face[0]
        val i1 = face[1]
        val i2 = face[2]
        val i3 = face[3]

        val cross = (projX[i1] - projX[i0]) * (projY[i2] - projY[i0]) -
            (projY[i1] - projY[i0]) * (projX[i2] - projX[i0])
        if (cross <= 0f) continue

        path.reset()
        path.moveTo(projX[i0], projY[i0])
        path.lineTo(projX[i1], projY[i1])
        path.lineTo(projX[i2], projY[i2])
        path.lineTo(projX[i3], projY[i3])
        path.close()

        val faceAlpha = (fillAlpha * FaceShadeMultipliers[f]).coerceIn(0.01f, 0.25f)
        drawPath(path = path, color = tint.copy(alpha = faceAlpha))
        drawPath(path = path, color = tint.copy(alpha = edgeAlpha), style = edgeStroke)
    }
}
