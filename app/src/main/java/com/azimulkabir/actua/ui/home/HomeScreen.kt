package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Stable Home root. Dashboard slices fill these keyed sections independently, preserving this
 * list's scroll position when a tab is reselected or individual section data changes.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    projection: HomeDashboardProjection = HomeDashboardProjection.empty(),
    hideDecimalPlaces: Boolean = false,
    onBudgetClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item(key = "home-header") { ActuaScreenHeader(title = "Home") }
        item(key = HomeSection.READY_TO_BUDGET.name) { Column { HomeSectionHeader(HomeSection.READY_TO_BUDGET.title); ReadyToBudgetCard(projection.budgetOverview, hideDecimalPlaces, onBudgetClick) } }
        item(key = HomeSection.FAVORITE_CATEGORIES.name) { Column { HomeSectionHeader(HomeSection.FAVORITE_CATEGORIES.title); CategoryRows(projection.favoriteCategories, hideDecimalPlaces, onBudgetClick) } }
        item(key = HomeSection.FAVORITE_ACCOUNTS.name) { Column { HomeSectionHeader(HomeSection.FAVORITE_ACCOUNTS.title); AccountRows(projection.favoriteAccounts, hideDecimalPlaces, onAccountsClick) } }
        item(key = HomeSection.UPCOMING.name) { Column { HomeSectionHeader(HomeSection.UPCOMING.title); ScheduleRows(projection.upcomingSchedules, hideDecimalPlaces, onSchedulesClick) } }
        item(key = HomeSection.THIS_MONTH.name) { Column { HomeSectionHeader(HomeSection.THIS_MONTH.title); ThisMonthCard(projection.monthTransactions, hideDecimalPlaces, onTransactionsClick) } }
        item(key = HomeSection.REPORTS.name) { Column { HomeSectionHeader(HomeSection.REPORTS.title); HomeDestinationRow("Dashboards and financial insights", Icons.Outlined.BarChart, onReportsClick) } }
        item(key = HomeSection.RECENT_ACTIVITY.name) { Column { HomeSectionHeader(HomeSection.RECENT_ACTIVITY.title); TransactionRows(projection.recentTransactions, hideDecimalPlaces, onTransactionsClick) } }
    }
}

@Composable
private fun HomeSectionHeader(title: String) = ActuaSectionHeader(title = title)

@Composable private fun ReadyToBudgetCard(overview: BudgetOverview, hideDecimals: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Ready to Budget", style = MaterialTheme.typography.labelLarge); Text(formatMoneyCents(overview.toBudgetCents ?: 0L, hideDecimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); if (overview.toBudgetCents == null) Text("No budget month selected", style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Outlined.PieChartOutline, contentDescription = null)
        }
    }
}

@Composable private fun CategoryRows(categories: List<BudgetCategory>, hideDecimals: Boolean, onClick: () -> Unit) {
    if (categories.isEmpty()) HomeEmptyRow("No favorite categories yet", onClick) else categories.take(5).forEach { HomeValueRow(it.name, "Available", it.balanceCents, hideDecimals, onClick) }
}
@Composable private fun AccountRows(accounts: List<Account>, hideDecimals: Boolean, onClick: () -> Unit) {
    if (accounts.isEmpty()) HomeEmptyRow("No favorite accounts yet", onClick) else accounts.take(5).forEach { HomeValueRow(it.name, it.type.replaceFirstChar { c -> c.uppercase() }, it.balanceCents, hideDecimals, onClick) }
}
@Composable private fun ScheduleRows(schedules: List<ScheduleListItem>, hideDecimals: Boolean, onClick: () -> Unit) {
    val visible = schedules.filter { it.status !in setOf(ScheduleStatus.COMPLETED, ScheduleStatus.PAID) }.take(5)
    if (visible.isEmpty()) HomeEmptyRow("No upcoming bills or schedules", onClick) else visible.forEach { HomeValueRow(it.title, scheduleLabel(it), it.schedule.postAmount, hideDecimals, onClick) }
}
@Composable private fun ThisMonthCard(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit) {
    var income = 0L
    var spending = 0L
    transactions.forEach {
        when {
            it.amountCents > 0L -> income += it.amountCents
            it.amountCents < 0L -> spending -= it.amountCents
        }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm).clickable(onClick = onClick)) { Row(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) { SummaryValue("Income", income, hideDecimals); SummaryValue("Spent", spending, hideDecimals); Column { Text("Activity", style = MaterialTheme.typography.labelMedium); Text(transactions.size.toString(), fontWeight = FontWeight.SemiBold) } } }
}
@Composable private fun SummaryValue(label: String, amount: Long, hideDecimals: Boolean) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatMoneyCents(amount, hideDecimals), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun TransactionRows(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit) { if (transactions.isEmpty()) HomeEmptyRow("No recent activity", onClick) else transactions.take(5).forEach { HomeValueRow(it.payee.ifBlank { "Transaction" }, it.category.ifBlank { it.account }, it.amountCents, hideDecimals, onClick) } }
@Composable private fun HomeValueRow(title: String, subtitle: String, amount: Long, hideDecimals: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(formatMoneyCents(amount, hideDecimals), fontWeight = FontWeight.SemiBold) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) }
@Composable private fun HomeEmptyRow(label: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) } }
@Composable private fun HomeDestinationRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null); Spacer(Modifier.width(Spacing.md)); Text(label, Modifier.weight(1f)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) } }
private fun scheduleLabel(item: ScheduleListItem): String {
    val due = item.schedule.nextDate?.let { date ->
        when (DayDate.today().daysUntil(date)) {
            in Int.MIN_VALUE..-1 -> "Overdue"
            0 -> "Due today"
            1 -> "Due tomorrow"
            else -> "Due ${date.iso}"
        }
    } ?: "Scheduled"
    return listOfNotNull(due, item.accountName?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
