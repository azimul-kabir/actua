package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.accounts.AccountMonthlySummaryCalculator
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.CategoryStatusDot
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.categoryStatusColor
import com.azimulkabir.actua.ui.theme.success
import com.azimulkabir.actua.ui.theme.warning
import kotlin.math.round

/**
 * Stable Home root. Each keyed module has its own visual hierarchy while retaining the
 * user-configured order and the authoritative routes/calculations owned by other screens.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    projection: HomeDashboardProjection = HomeDashboardProjection.empty(),
    sections: List<HomeSection> = HomeSection.entries,
    hideDecimalPlaces: Boolean = false,
    onBudgetClick: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onAccountClick: (String) -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onTransactionsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onReportClick: (String) -> Unit = {},
    onCustomizeClick: () -> Unit = {},
    returnToRootRequest: Int = 0,
    hasFab: Boolean = true,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("homeList"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        contentPadding = PaddingValues(bottom = if (hasFab) 160.dp else 0.dp),
    ) {
        item(key = "home-header") {
            ActuaScreenHeader(title = "Home") {
                IconButton(onClick = onCustomizeClick) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Customize Home")
                }
            }
        }
        sections.forEach { section ->
            item(key = section.name) {
                when (section) {
                    HomeSection.READY_TO_BUDGET -> ReadyToBudgetHero(
                        projection.budgetOverview, hideDecimalPlaces, onBudgetClick)
                    HomeSection.FAVORITE_CATEGORIES -> FavoriteCategoriesSection(
                        projection.favoriteCategories, hideDecimalPlaces, onBudgetClick, onCategoryClick)
                    HomeSection.FAVORITE_ACCOUNTS -> FavoriteAccountsSection(
                        projection.favoriteAccounts, hideDecimalPlaces, onAccountsClick, onAccountClick)
                    HomeSection.UPCOMING -> UpcomingSection(
                        projection.upcomingSchedules, hideDecimalPlaces, onSchedulesClick)
                    HomeSection.THIS_MONTH -> ThisMonthSection(
                        projection.monthTransactions, hideDecimalPlaces, onTransactionsClick)
                    HomeSection.REPORTS -> ReportsSection(
                        projection.favoriteReports, onReportClick, onReportsClick)
                    HomeSection.RECENT_ACTIVITY -> RecentActivitySection(
                        projection.recentTransactions, hideDecimalPlaces, onTransactionsClick)
                }
            }
        }
    }
}

@Composable
private fun ReadyToBudgetHero(overview: BudgetOverview, hideDecimals: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
        Text(HomeSection.READY_TO_BUDGET.title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.sm))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("readyToBudgetHero"),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        ) {
            Row(Modifier.fillMaxWidth().padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Available to assign", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                    Text(formatMoneyCents(overview.toBudgetCents ?: 0L, hideDecimals),
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (overview.toBudgetCents == null) "No budget month selected" else "Open current budget",
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
                Surface(shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)) {
                    Icon(Icons.Outlined.PieChartOutline, null, Modifier.padding(Spacing.md).size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoriteCategoriesSection(categories: List<BudgetCategory>, hideDecimals: Boolean,
    onViewBudgetClick: () -> Unit, onCategoryClick: (String) -> Unit) {
    DashboardSectionHeader(HomeSection.FAVORITE_CATEGORIES.title, "View budget", onViewBudgetClick)
    if (categories.isEmpty()) {
        DashboardEmptyCard("No favorite categories yet", onViewBudgetClick)
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        items(categories.take(5), key = { it.id ?: it.name }) { category ->
            CategoryProgressCard(category, hideDecimals) { onCategoryClick(category.name) }
        }
    }
}

@Composable
private fun CategoryProgressCard(category: BudgetCategory, hideDecimals: Boolean, onClick: () -> Unit) {
    val fraction = category.progressFraction()
    val progressColor = categoryProgressColor(category)
    val percent = round(fraction * 100).toInt()
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Card(
        modifier = Modifier.width(if (largeText) 208.dp else 176.dp).clickable(onClick = onClick)
            .testTag("favoriteCategoryCard"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.heightIn(min = if (largeText) 188.dp else 164.dp).padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryStatusDot(category.progressState, modifier = Modifier.padding(end = 6.dp))
                    Text(category.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text("${formatMoneyCents(category.balanceCents, hideDecimals)} available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (category.balanceCents < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column {
                Text(categoryProgressLabel(category, percent), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(PillShape)
                        .semantics { stateDescription = categoryProgressLabel(category, percent) },
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun FavoriteAccountsSection(accounts: List<Account>, hideDecimals: Boolean, onViewAccountsClick: () -> Unit,
    onAccountClick: (String) -> Unit) {
    DashboardSectionHeader(HomeSection.FAVORITE_ACCOUNTS.title, "View accounts", onViewAccountsClick)
    if (accounts.isEmpty()) {
        DashboardEmptyCard("No favorite accounts yet", onViewAccountsClick)
        return
    }
    val singleColumn = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        accounts.take(5).chunked(if (singleColumn) 1 else 2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEach { account ->
                    AccountCard(account, hideDecimals, { onAccountClick(account.name) }, Modifier.weight(1f))
                }
                if (!singleColumn && pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccountCard(account: Account, hideDecimals: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.padding(Spacing.sm).size(20.dp))
            }
            Spacer(Modifier.height(Spacing.md))
            Text(account.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(account.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.sm))
            Text(formatMoneyCents(account.balanceCents, hideDecimals),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UpcomingSection(schedules: List<ScheduleListItem>, hideDecimals: Boolean, onClick: () -> Unit) {
    val visible = schedules.filter { it.status !in setOf(ScheduleStatus.COMPLETED, ScheduleStatus.PAID) }.take(5)
    DashboardSectionHeader(HomeSection.UPCOMING.title, "View calendar", onClick)
    if (visible.isEmpty()) {
        DashboardEmptyCard("No upcoming bills or schedules", onClick)
        return
    }
    DashboardSurface {
        visible.forEachIndexed { index, item ->
            UpcomingRow(item, hideDecimals, onClick)
            if (index != visible.lastIndex) DashboardDivider()
        }
        DashboardFooter("View calendar", Icons.Outlined.CalendarMonth, onClick)
    }
}

@Composable
private fun UpcomingRow(item: ScheduleListItem, hideDecimals: Boolean, onClick: () -> Unit) {
    val statusColor = scheduleStatusColor(item.status)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scheduleLabel(item), style = MaterialTheme.typography.bodySmall,
                color = if (item.status == ScheduleStatus.MISSED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatMoneyCents(item.schedule.postAmount, hideDecimals), fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}

@Composable
private fun ThisMonthSection(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit) {
    val summary = homeMonthActivity(transactions)
    val income = summary.incomeCents
    val spending = summary.expenseCents
    val net = income - spending
    val scale = maxOf(income, spending, 1L).toFloat()
    DashboardSectionHeader(HomeSection.THIS_MONTH.title, "View activity", onClick)
    Card(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal).fillMaxWidth()
        .clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MonthMetric("Income", income, hideDecimals, MaterialTheme.colorScheme.success, Modifier.weight(1f))
                MonthMetric("Spent", spending, hideDecimals, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MonthMetric("Net", net, hideDecimals,
                    if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.lg))
            MonthBar("Income", income / scale, MaterialTheme.colorScheme.success)
            Spacer(Modifier.height(Spacing.sm))
            MonthBar("Spending", spending / scale, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.lg))
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Text("Activity", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("· ${transactions.size} transactions", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Uses the same category-aware calculation as Accounts so transfers do not become cash flow. */
internal fun homeMonthActivity(transactions: List<Transaction>) =
    AccountMonthlySummaryCalculator.calculate(transactions)

@Composable
private fun MonthMetric(label: String, amount: Long, hideDecimals: Boolean, color: Color,
    modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MonthBar(label: String, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(58.dp))
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp).clip(PillShape), color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp,
            drawStopIndicator = {})
    }
}

@Composable
private fun ReportsSection(reports: List<ReportDashboardPage>, onReportClick: (String) -> Unit,
    onViewAllClick: () -> Unit) {
    DashboardSectionHeader(HomeSection.REPORTS.title, "View all", onViewAllClick)
    DashboardSurface {
        if (reports.isEmpty()) {
            InsightRow("Dashboards and financial insights", "Explore trends across your budget", onViewAllClick)
        } else {
            reports.take(5).forEachIndexed { index, report ->
                InsightRow(report.name, "Open dashboard") { onReportClick(report.id) }
                if (index != reports.take(5).lastIndex) DashboardDivider()
            }
            DashboardFooter("View all reports", Icons.Outlined.BarChart, onViewAllClick)
        }
    }
}

@Composable
private fun InsightRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Icon(Icons.Outlined.BarChart, null, Modifier.padding(Spacing.md).size(24.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

@Composable
private fun RecentActivitySection(transactions: List<Transaction>, hideDecimals: Boolean,
    onClick: () -> Unit) {
    DashboardSectionHeader(HomeSection.RECENT_ACTIVITY.title, "View all", onClick)
    if (transactions.isEmpty()) {
        DashboardEmptyCard("No recent activity", onClick)
        return
    }
    DashboardSurface {
        transactions.take(5).forEachIndexed { index, transaction ->
            ActivityRow(transaction, hideDecimals, onClick)
            if (index != transactions.take(5).lastIndex) DashboardDivider()
        }
        DashboardFooter("View all activity", Icons.AutoMirrored.Outlined.ReceiptLong, onClick)
    }
}

@Composable
private fun ActivityRow(transaction: Transaction, hideDecimals: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(transaction.payee.ifBlank { "T" }.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(transaction.payee.ifBlank { "Transaction" }, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(transaction.category.ifBlank { transaction.account }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text(formatMoneyCents(transaction.amountCents, hideDecimals), fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}

@Composable
private fun DashboardSectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(action, modifier = Modifier.clip(PillShape).clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(Spacing.sm))
}

@Composable
private fun DashboardSurface(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(content = content)
    }
}

@Composable
private fun DashboardEmptyCard(label: String, onClick: () -> Unit) {
    DashboardSurface {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun DashboardFooter(label: String, icon: ImageVector, onClick: () -> Unit) {
    DashboardDivider()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DashboardDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun categoryProgressColor(category: BudgetCategory): Color {
    val colors = MaterialTheme.colorScheme
    return if (category.usesGoalProgress) {
        if (category.balanceCents < 0L) colors.error else colors.primary
    } else categoryStatusColor(category.progressState)
}

private fun categoryProgressLabel(category: BudgetCategory, percent: Int): String =
    if (category.usesGoalProgress) "$percent% funded toward goal"
    else "${category.progressState.label} · $percent% spent"

@Composable
private fun scheduleStatusColor(status: ScheduleStatus): Color = when (status) {
    ScheduleStatus.MISSED -> MaterialTheme.colorScheme.error
    ScheduleStatus.DUE -> MaterialTheme.colorScheme.warning
    ScheduleStatus.UPCOMING -> MaterialTheme.colorScheme.primary
    ScheduleStatus.PAID, ScheduleStatus.COMPLETED -> MaterialTheme.colorScheme.success
    ScheduleStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
}

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
