package com.azimulkabir.actua.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal fun detailNavigationMotion(opening: Boolean, direction: Int = 1): ContentTransform {
    val enterDirection = if (opening) direction else -direction
    return (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { enterDirection * it / 5 }) togetherWith
        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -enterDirection * it / 10 })
}
