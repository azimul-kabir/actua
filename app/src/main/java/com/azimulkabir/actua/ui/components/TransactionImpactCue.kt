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
private const val AUTO_DISMISS_MILLIS = 4200L

/**
 * A snapshot of how saving, editing, deleting or duplicating a transaction moved one budget
 * category's available balance, used to drive [TransactionImpactPopup]. An empty list means
 * nothing to show; more than one entry shows up when the change touches more than one category
 * (a split transaction, or an edit that moves the transaction to a different category).
 */
data class TransactionImpactCue(
    val categoryName: String,
    val balanceBeforeCents: Long,
    val balanceAfterCents: Long,
) {
    val deltaCents: Long get() = balanceAfterCents - balanceBeforeCents
    val isExpense: Boolean get() = deltaCents < 0
}

/**
 * A polished, auto-dismissing popup that slides up from the bottom of the screen to show
 * the before/impact/after available balance of every budget category a transaction change
 * touched, then slides back down. Red for a category that lost money, green for one that
 * gained it, matching the rest of the app's status colors.
 */
@Composable
fun TransactionImpactPopup(
    cues: List<TransactionImpactCue>,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(cues) {
        if (cues.isNotEmpty()) {
            delay(AUTO_DISMISS_MILLIS)
            onDismiss()
        }
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 96.dp)) {
        AnimatedVisibility(
            visible = cues.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).testTag("transactionImpactCue"),
            enter = slideInVertically(animationSpec = tween(320)) { it } + fadeIn(tween(320)),
            exit = slideOutVertically(animationSpec = tween(260)) { it } + fadeOut(tween(260)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                cues.forEach { cue -> TransactionImpactCard(cue, hideDecimalPlaces) }
            }
        }
    }
}

@Composable
private fun TransactionImpactCard(cue: TransactionImpactCue, hideDecimalPlaces: Boolean) {
    val accentColor = if (cue.isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.success
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                    cue.categoryName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        formatMoneyCents(cue.balanceBeforeCents, hideDecimalPlaces, respectBalanceVisibility = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoneyCents(cue.deltaCents, hideDecimalPlaces, showPositiveSign = true, respectBalanceVisibility = false),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoneyCents(cue.balanceAfterCents, hideDecimalPlaces, respectBalanceVisibility = false),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
