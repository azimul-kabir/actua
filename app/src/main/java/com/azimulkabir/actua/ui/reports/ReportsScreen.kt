package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.max

@Composable
fun ReportsScreen(snapshot: ReportSnapshot, hideDecimalPlaces: Boolean, modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}, scrollToTopRequest: Int = 0) {
    val current = snapshot.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search Actua") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Income", current?.incomeCents ?: 0, hideDecimalPlaces, Modifier.weight(1f))
                SummaryCard("Expenses", current?.expenseCents ?: 0, hideDecimalPlaces, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Net", current?.netCents ?: 0, hideDecimalPlaces, Modifier.weight(1f))
                SummaryCard("Net worth", snapshot.netWorthCents, hideDecimalPlaces, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Cash flow · last 6 months") }
        item { CashFlowBars(snapshot, hideDecimalPlaces) }
        item { SectionTitle("Spending by category · this month") }
        if (snapshot.categories.isEmpty()) {
            item { Text("No categorized spending this month.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            val largest = snapshot.categories.maxOf { it.spentCents }.coerceAtLeast(1)
            items(snapshot.categories, key = { it.name }) { category ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(category.name, modifier = Modifier.weight(1f))
                        Text(formatMoneyCents(category.spentCents, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(
                        Modifier.fillMaxWidth(category.spentCents.toFloat() / largest)
                            .height(7.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, amount: Long, hideDecimals: Boolean, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun CashFlowBars(snapshot: ReportSnapshot, hideDecimals: Boolean) {
    val maximum = snapshot.months.maxOfOrNull { max(it.incomeCents, it.expenseCents) }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        snapshot.months.forEach { month ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(month.month, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                    Text("In ${formatMoneyCents(month.incomeCents, hideDecimals)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("Out ${formatMoneyCents(month.expenseCents, hideDecimals)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(Modifier.fillMaxWidth(month.incomeCents.toFloat() / maximum).height(6.dp)
                        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
