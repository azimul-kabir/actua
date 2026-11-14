package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Runs [onRequest] once for each increase of [request], the counter a tab bumps when its tab is re-tapped.
 *
 * A plain `LaunchedEffect(request)` also runs when the screen enters composition, so a tab that was re-tapped
 * once would reset itself on every later return from another tab. Starting from the value seen on entry means
 * only taps made while the screen is shown count.
 */
@Composable
fun RootRequestEffect(request: Int, onRequest: suspend () -> Unit) {
    var handled by rememberSaveable { mutableIntStateOf(request) }
    LaunchedEffect(request) {
        if (request > handled) {
            handled = request
            onRequest()
        }
    }
}
