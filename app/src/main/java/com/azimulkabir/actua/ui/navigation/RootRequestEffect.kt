package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Runs [onRequest] once for each increase of [request], the counter a tab bumps when its tab is re-tapped.
 *
 * A plain `LaunchedEffect(request)` also runs when the screen enters composition, so a tab that was re-tapped
 * once would reset itself on every later return from another tab. Starting from the value seen on entry means
 * only taps made while the screen is shown count. That is a plain `remember`, not `rememberSaveable`: the counters
 * are not saved, so after the activity is recreated they start again from 0, and a restored, higher handled value
 * would swallow the next re-taps.
 */
@Composable
fun RootRequestEffect(request: Int, onRequest: suspend () -> Unit) {
    var handled by remember { mutableIntStateOf(request) }
    LaunchedEffect(request) {
        if (request > handled) {
            handled = request
            onRequest()
        }
    }
}
