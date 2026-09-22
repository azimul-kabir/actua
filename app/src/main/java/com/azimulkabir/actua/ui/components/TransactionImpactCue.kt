package com.azimulkabir.actua.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.success
import kotlinx.coroutines.delay

/** How long a [TransactionImpactPopup] stays up before it auto-dismisses. */
private const val AUTO_DISMISS_MILLIS = 2600L

/**
 * A snapshot of how a just-saved transaction moved an account's balance, used to
 * drive [TransactionImpactPopup]. `null` means nothing to show.
 */
data class TransactionImpactCue(
    val accountName: String,
    val balanceBeforeCents: Long,
    val balanceAfterCents: Long,
    val isExpense: Boolean,
)

/**
 * A polished, auto-dismissing popup that slides up from the bottom of the screen to show
 * the before/after balance impact of a transaction, then slides back down. Red for an
 * expense, green for income, matching the rest of the app's status colors.
 */
@Composable
fun TransactionImpactPopup(
    cue: TransactionImpactCue?,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(cue) {
        if (cue != null) {
            delay(AUTO_DISMISS_MILLIS)
            onDismiss()
        }
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 96.dp)) {
        AnimatedVisibility(
            visible = cue != null,
            modifier = Modifier.align(Alignment.BottomCenter).testTag("transactionImpactCue"),
            enter = slideInVertically(animationSpec = tween(320)) { it } + fadeIn(tween(320)),
            exit = slideOutVertically(animationSpec = tween(260)) { it } + fadeOut(tween(260)),
        ) {
            if (cue != null) TransactionImpactCard(cue, hideDecimalPlaces)
        }
    }
}

@Composable
private fun TransactionImpactCard(cue: TransactionImpactCue, hideDecimalPlaces: Boolean) {
    val accentColor = if (cue.isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.success
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = if (cue.isExpense) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = accentColor,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    cue.accountName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        formatMoneyCents(cue.balanceBeforeCents, hideDecimalPlaces, respectBalanceVisibility = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoneyCents(cue.balanceAfterCents, hideDecimalPlaces, respectBalanceVisibility = false),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
            }
        }
    }
}
