package com.azimulkabir.actua.ui.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Backdrop blur needs the platform RenderEffect, which exists from Android 12 (API 31). */
internal val BackdropBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The screen content as the floating tab bar sees it from behind. The content records itself into [layer]
 * while it draws ([recordsBackdrop]); the bar draws a blurred copy of the part under it. [revision] changes
 * on every recording so the bar redraws whenever the content behind it does, for instance while scrolling.
 */
@Stable
internal class TabBarBackdrop(val layer: GraphicsLayer) {
    var contentOrigin: Offset = Offset.Zero
    var revision by mutableLongStateOf(0L)
        private set

    private var recordings = 0L

    /**
     * Marks the content as re-recorded so the bar redraws. It is called while the content draws, so it must
     * only write [revision]: reading it there (as `revision += 1` would) makes the content observe its own
     * change, and it would then redraw, and write, on every frame, forever.
     */
    fun markRecorded() {
        revision = ++recordings
    }
}

@Composable
internal fun rememberTabBarBackdrop(): TabBarBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { TabBarBackdrop(layer) }
}

/** Draws this content as usual and also records it for [backdrop]. A no-op below API 31. */
internal fun Modifier.recordsBackdrop(backdrop: TabBarBackdrop): Modifier =
    if (!BackdropBlurSupported) this
    else this
        .onGloballyPositioned { backdrop.contentOrigin = it.positionInWindow() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
            backdrop.markRecorded()
        }

private class WindowOrigin { var value: Offset = Offset.Zero }

/**
 * Paints, behind this element, a blurred copy of what [backdrop] recorded for the area it covers, then [tint]
 * over it. Only call it where [BackdropBlurSupported]. The element's window position is tracked so the right
 * part of the content is drawn under it, also while the bar slides in and out.
 */
@Composable
internal fun Modifier.blurredBackdrop(backdrop: TabBarBackdrop, tint: Color, radius: Dp = 24.dp): Modifier {
    val blurLayer = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    SideEffect { blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp) }
    val origin = remember { WindowOrigin() }
    return this
        .onGloballyPositioned { origin.value = it.positionInWindow() }
        .drawBehind {
            backdrop.revision // read, so this redraws whenever the content behind changes
            val delta = backdrop.contentOrigin - origin.value
            blurLayer.record(this, layoutDirection, IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                translate(delta.x, delta.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(blurLayer)
            drawRect(tint)
        }
}
