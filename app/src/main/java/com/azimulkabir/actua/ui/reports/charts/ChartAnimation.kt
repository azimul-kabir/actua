package com.azimulkabir.actua.ui.reports.charts

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Entrance animation length for lines, bars and slices. */
const val CHART_REVEAL_MILLIS = 450

/** Chart animations follow the system "remove animations" setting: a duration scale of 0 turns them off. */
fun chartAnimationsEnabled(animatorDurationScale: Float): Boolean = animatorDurationScale > 0f

/**
 * Progress from 0 to 1 for a chart's entrance animation, replayed when [key] changes (new data or a
 * new range, not a selection). Read `.value` inside a draw block so frames don't recompose. When the
 * system animator duration scale is 0 it starts at 1, so nothing animates.
 */
@Composable
fun rememberChartReveal(key: Any?): Animatable<Float, AnimationVector1D> {
    val resolver = LocalContext.current.contentResolver
    val enabled = remember(resolver) {
        chartAnimationsEnabled(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    val progress = remember(key) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(progress) {
        if (enabled) progress.animateTo(1f, tween(CHART_REVEAL_MILLIS, easing = FastOutSlowInEasing))
    }
    return progress
}
