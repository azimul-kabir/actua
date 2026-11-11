package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.RatingPromptPreferences
import com.azimulkabir.actua.data.preferences.SupportPromptPreferences
import com.azimulkabir.actua.data.preferences.SupporterPerks
import com.azimulkabir.actua.review.appReviewAvailable
import com.azimulkabir.actua.review.launchAppReview
import kotlinx.coroutines.launch

/**
 * The one-time "rate Actua" request, shown after [RatingPromptPreferences.DAYS_THRESHOLD] days and
 * [RatingPromptPreferences.TRANSACTION_THRESHOLD] added transactions.
 *
 * It first asks whether Actua is useful, and only a "Yes" opens Google's review sheet, so unhappy
 * users are not sent to the store. Once shown it is recorded straight away, so it can't come back,
 * whichever button is pressed. The fdroid build and installs that did not come from Google Play never show it. It waits for the Support Actua
 * prompt when that one is still due, so the two never appear together. [canShow] lets the caller
 * hold it back, e.g. while another dialog or a detail screen is open.
 */
@Composable
fun RatingPrompt(canShow: Boolean) {
    val context = LocalContext.current
    if (!appReviewAvailable(context)) return
    val activity = LocalActivity.current
    val preferences = remember { RatingPromptPreferences(context) }
    // Read once: after it is marked asked, isDue() turns false, which must not hide the open dialog.
    val due = remember {
        preferences.isDue() &&
            !supportPromptPending(SupportPromptPreferences(context).isDue(), SupporterPerks(context).isSupporter)
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    // Created before the early return: the scope must outlive the dialog to open the review sheet.
    val scope = rememberCoroutineScope()
    if (!due || dismissed || !canShow) return

    LaunchedEffect(Unit) { preferences.asked = true }
    AlertDialog(
        onDismissRequest = { dismissed = true },
        icon = { Icon(Icons.Outlined.StarBorder, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_rating_prompt_title)) },
        text = { Text(stringResource(R.string.settings_rating_prompt_body)) },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                activity?.let { scope.launch { launchAppReview(it) } }
            }) { Text(stringResource(R.string.settings_rating_prompt_yes)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.settings_rating_prompt_no)) }
        },
    )
}

/**
 * The Support Actua prompt still wants to be shown: it is due and unseen, and the user isn't a
 * supporter (a supporter is never asked, so it must not hold this one back forever).
 */
internal fun supportPromptPending(supportPromptDue: Boolean, isSupporter: Boolean): Boolean =
    supportPromptDue && !isSupporter
