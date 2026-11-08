package com.myra.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myra.ai.ui.theme.MyraBlue

enum class OrbState(val periodMs: Int, val amplitude: Float, val waves: Int) {
    Idle(3200, 0.04f, 0),
    Listening(1400, 0.10f, 1),
    Thinking(700, 0.06f, 2),
    Speaking(900, 0.14f, 3)
}

@Composable
fun MyraOrb(state: OrbState, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "orb")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(state.periodMs, easing = LinearEasing)),
        label = "phase"
    )
    val pulse = 1f + state.amplitude * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2.8f * pulse
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 2f),
                radius = r * 2f, center = c
            )
            // waveform rings while active
            for (i in 1..state.waves) {
                val p = (phase + i / (state.waves + 1f)) % 1f
                drawCircle(
                    accent.copy(alpha = (1f - p) * 0.5f),
                    radius = r * (1f + p * 0.8f), center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
            drawCircle(
                Brush.linearGradient(listOf(accent, MyraBlue), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)),
                radius = r, center = c
            )
            drawCircle(Color.White.copy(alpha = 0.18f), radius = r * 0.45f, center = Offset(c.x - r * 0.3f, c.y - r * 0.3f))
        }
    }
}
