package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A drag handle that starts reordering immediately on touch (no long-press wait) since the handle
 * itself is already a dedicated, large tap target — the rest of the row keeps its normal click/scroll
 * behavior. [key] should identify the row so the gesture detector resets when the row's identity changes.
 *
 * The callbacks are captured through [rememberUpdatedState] so the drag, once started, always calls the
 * latest [onDrag]/[onDragEnd] even though the underlying `pointerInput` coroutine (keyed on [key], not on
 * every recomposition) is not relaunched as the dragged item's own local state changes underneath it.
 */
@Composable
fun Modifier.dragReorderHandle(
    key: Any,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    return this.pointerInput(key) {
        detectDragGestures(
            onDragStart = { currentOnDragStart() },
            onDragEnd = { currentOnDragEnd() },
            onDragCancel = { currentOnDragEnd() },
            onDrag = { change, offset -> change.consume(); currentOnDrag(offset.y) },
        )
    }
}
