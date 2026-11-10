package com.azimulkabir.actua.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.SupportPurchaseState
import com.azimulkabir.actua.billing.createSupportBillingManager
import com.azimulkabir.actua.data.preferences.SupportPromptPreferences
import com.azimulkabir.actua.data.preferences.SupporterPerks

/**
 * The one-time "Support Actua" prompt, shown after [SupportPromptPreferences.DAYS_THRESHOLD] days
 * or [SupportPromptPreferences.TRANSACTION_THRESHOLD] added transactions, whichever comes first.
 *
 * It waits for Play to confirm the options and the user's purchases, so it never asks a
 * supporter, and the fdroid build (where billing is never available) never shows it. Once shown
 * it is recorded straight away, so it can't come back, whichever button is pressed.
 * [canShow] lets the caller hold it back, e.g. while another dialog or a detail screen is open.
 */
@Composable
fun SupportPrompt(canShow: Boolean, onOpenSupport: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { SupportPromptPreferences(context) }
    // Read once: after it is marked shown, isDue() turns false, which must not hide the open dialog.
    val due = remember { preferences.isDue() }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!due || dismissed) return

    // Only users due for the prompt open a Play connection, once, to check supporter status.
    val billingManager = remember { createSupportBillingManager(context) }
    DisposableEffect(billingManager) {
        billingManager.start()
        onDispose { billingManager.stop() }
    }
    val state by billingManager.state.collectAsState()
    LaunchedEffect(state.purchasesRestored, state.isSupporter) { SupporterPerks(context).record(state) }
    if (!canShow || !shouldAskForSupport(state)) return

    LaunchedEffect(Unit) { preferences.shown = true }
    AlertDialog(
        onDismissRequest = { dismissed = true },
        icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_support_prompt_title)) },
        text = { Text(stringResource(R.string.settings_support_prompt_body)) },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                onOpenSupport()
            }) { Text(stringResource(R.string.settings_support_prompt_see_options)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.settings_support_prompt_not_now)) }
        },
    )
}

/** Ask only when Play offers something and has confirmed the user isn't already a supporter. */
internal fun shouldAskForSupport(state: SupportPurchaseState): Boolean =
    state.isAvailable && state.purchasesRestored && !state.isSupporter
