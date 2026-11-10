package com.azimulkabir.actua.ui.celebrations

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.SupporterPerks
import com.azimulkabir.actua.ui.theme.success
import com.azimulkabir.actua.ui.theme.warning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Starts a celebration if the user may see one; a no-op outside [CelebrationHost] and on the fdroid build. */
val LocalCelebrate = staticCompositionLocalOf<() -> Unit> { {} }

private const val DURATION_MILLIS = 1500
private const val PARTICLES = 90

/**
 * Provides [LocalCelebrate] and draws the confetti over [content]. Confetti is decoration only: it
 * never takes touches and is hidden from screen readers.
 */
@Composable
fun CelebrationHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var playId by remember { mutableIntStateOf(0) }
    val celebrate = remember(context) {
        {
            if (shouldPlay(context)) playId += 1
        }
    }
    CompositionLocalProvider(LocalCelebrate provides celebrate) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (playId > 0) Confetti(playId, Modifier.fillMaxSize())
        }
    }
}

private fun shouldPlay(context: Context): Boolean = CelebrationPolicy.shouldPlay(
    available = supporterPerksAvailable(),
    isSupporter = SupporterPerks(context).isSupporter,
    enabled = DisplayPreferences(context).celebrationsEnabled,
    animationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
)

private class Particle(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

/** A short burst from the top centre that falls under gravity and fades out; plays once per [playId]. */
@Composable
private fun Confetti(playId: Int, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.success,
        MaterialTheme.colorScheme.warning,
    )
    val particles = remember(playId, colors) {
        val random = Random(playId)
        List(PARTICLES) {
            Particle(
                // Mostly downward and outward from the top centre.
                angle = (PI * (0.15 + 0.7 * random.nextDouble())).toFloat(),
                speed = 0.5f + random.nextFloat() * 0.9f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                width = 6f + random.nextFloat() * 6f,
                height = 3f + random.nextFloat() * 4f,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    val progress = remember(playId) { Animatable(0f) }
    LaunchedEffect(playId) { progress.animateTo(1f, tween(DURATION_MILLIS, easing = LinearEasing)) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val unit = size.width.coerceAtLeast(size.height)
        val fade = if (t > 0.75f) (1f - t) / 0.25f else 1f
        particles.forEach { p ->
            // Launch speed spreads the burst; gravity pulls it down.
            val distance = p.speed * unit * 0.6f * t
            val x = size.width / 2f + cos(p.angle) * distance
            val y = -16.dp.toPx() + sin(p.angle) * distance + 0.5f * unit * 1.1f * t * t
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.width.dp.toPx() / 2f, y - p.height.dp.toPx() / 2f),
                    size = Size(p.width.dp.toPx(), p.height.dp.toPx()),
                )
            }
        }
    }
}
