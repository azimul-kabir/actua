package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.WhatsNewNotes
import com.azimulkabir.actua.data.preferences.WhatsNewPreferences

/**
 * The "What's new" card, shown once on the first open after an update, never on a fresh install.
 * The version is recorded as soon as the card appears (or at once when there is nothing to show),
 * so it can't return, whichever way it is closed. [canShow] lets the caller hold it back, e.g.
 * while another dialog or a detail screen is open.
 */
@Composable
fun WhatsNewPrompt(canShow: Boolean) {
    val context = LocalContext.current
    val preferences = remember { WhatsNewPreferences(context) }
    // Read once: after the version is recorded the decision turns false, which must not hide the open card.
    val notes = remember {
        val current = BuildConfig.VERSION_CODE
        val show = WhatsNewPreferences.shouldShow(preferences.lastSeenVersionCode, current, preferences.isFreshInstall())
        val lines = if (show) WhatsNewNotes.load(context) else emptyList()
        if (lines.isEmpty() && preferences.lastSeenVersionCode != current) preferences.lastSeenVersionCode = current
        lines
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (notes.isEmpty() || dismissed || !canShow) return

    LaunchedEffect(Unit) { preferences.lastSeenVersionCode = BuildConfig.VERSION_CODE }
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.settings_whats_new_title, BuildConfig.VERSION_NAME)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                notes.forEach { line -> Text(line) }
            }
        },
        confirmButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.settings_whats_new_dismiss)) }
        },
    )
}
