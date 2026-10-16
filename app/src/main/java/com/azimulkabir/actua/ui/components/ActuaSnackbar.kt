package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Test tag on every snackbar the app shows, so tests can find the container itself. */
const val ActuaSnackbarTag = "actuaSnackbar"

/**
 * The app's snackbar host. It replaces Material's default near-black (inverse surface) snackbar
 * with the same tonal surface, corner radius and accent colour as the app's cards and sheets, so
 * messages such as "No network" or "Merged transactions" read as part of Actua rather than as a
 * system or developer message.
 */
@Composable
fun ActuaSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> ActuaSnackbar(data) }
}

@Composable
fun ActuaSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Snackbar(
        snackbarData = data,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .testTag(ActuaSnackbarTag),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        actionContentColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
