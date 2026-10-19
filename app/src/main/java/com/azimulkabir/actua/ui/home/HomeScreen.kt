package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.home.HomeSummaryPeriod
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.accounts.AccountMonthlySummaryCalculator
import com.azimulkabir.actua.ui.accounts.accountTypeIcon
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaHeroSize
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.CategoryStatusDot
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Sizes
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(bottom = if (hasFab) 160.dp else 0.dp),
    ) {
        item(key = "home-header") {
            ActuaScreenHeader(title = stringResource(R.string.home_title)) {
                IconButton(onClick = onCustomizeClick) {
                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_customize))
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
                        projection.monthTransactions, hideDecimalPlaces, onTransactionsClick, projection.summaryPeriod)
                    HomeSection.REPORTS -> ReportsSection(
                        projection.favoriteReports, onReportClick, onReportsClick)
                    HomeSection.RECENT_ACTIVITY -> RecentActivitySection(
                        projection.recentTransactions, hideDecimalPlaces, onTransactionsClick)
                }
            }
        }
    }
}

/** The medium hero amount from Add transaction, inside the tinted Ready to Budget container. */
@Composable
private fun ReadyToBudgetHero(overview: BudgetOverview, hideDecimals: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
        ActuaGroupLabel(stringResource(HomeSection.READY_TO_BUDGET.titleRes))
        Surface(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClickLabel = stringResource(R.string.home_open_budget), onClick = onClick).testTag("readyToBudgetHero"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            ActuaHeroAmount(
                amount = formatMoneyCents(overview.toBudgetCents ?: 0L, hideDecimals),
                modifier = Modifier.padding(vertical = Spacing.md),
                caption = stringResource(R.string.home_available_to_assign),
                amountColor = MaterialTheme.colorScheme.onPrimaryContainer,
                captionColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                size = ActuaHeroSize.Medium,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (overview.toBudgetCents == null) stringResource(R.string.home_no_budget_month)
                        else stringResource(R.string.home_open_current_budget),
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun FavoriteCategoriesSection(categories: List<BudgetCategory>, hideDecimals: Boolean,
    onViewBudgetClick: () -> Unit, onCategoryClick: (String) -> Unit) {
    DashboardSectionHeader(stringResource(HomeSection.FAVORITE_CATEGORIES.titleRes),
        stringResource(R.string.home_view_budget), onViewBudgetClick)
    if (categories.isEmpty()) {
        DashboardEmptyCard(stringResource(R.string.home_no_favorite_categories), onViewBudgetClick)
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
    // Resolved once: shown as text and reused as the progress bar's state description.
    val progressLabel = categoryProgressLabel(category, percent)
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier.width(if (largeText) 208.dp else 176.dp).clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick).testTag("favoriteCategoryCard"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.heightIn(min = if (largeText) 188.dp else 164.dp).padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryStatusDot(category.statusState(), modifier = Modifier.padding(end = 6.dp))
                    Text(category.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.home_amount_available, formatMoneyCents(category.balanceCents, hideDecimals)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (category.balanceCents < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column {
                Text(progressLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(PillShape)
                        .semantics { stateDescription = progressLabel },
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
    DashboardSectionHeader(stringResource(HomeSection.FAVORITE_ACCOUNTS.titleRes),
        stringResource(R.string.home_view_accounts), onViewAccountsClick)
    if (accounts.isEmpty()) {
        DashboardEmptyCard(stringResource(R.string.home_no_favorite_accounts), onViewAccountsClick)
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
    Surface(modifier = modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Icon(accountTypeIcon(account.type, isCreditCard = false), null,
                    Modifier.padding(Spacing.sm).size(20.dp))
            }
            Spacer(Modifier.height(Spacing.md))
            Text(account.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(accountTypeLabel(account.type), style = MaterialTheme.typography.labelMedium,
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
    DashboardSectionHeader(stringResource(HomeSection.UPCOMING.titleRes), stringResource(R.string.home_view_calendar), onClick)
    if (visible.isEmpty()) {
        DashboardEmptyCard(stringResource(R.string.home_no_upcoming), onClick)
        return
    }
    DashboardSurface {
        visible.forEachIndexed { index, item ->
            UpcomingRow(item, hideDecimals, onClick)
            if (index != visible.lastIndex) DashboardDivider()
        }
        DashboardFooter(stringResource(R.string.home_view_calendar), Icons.Outlined.CalendarMonth, onClick)
    }
}

@Composable
private fun UpcomingRow(item: ScheduleListItem, hideDecimals: Boolean, onClick: () -> Unit) {
    val statusColor = scheduleStatusColor(item.status)
    DashboardRow(onClick) {
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
        Text(formatMoneyCents(item.schedule.postAmount, hideDecimals), style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ThisMonthSection(transactions: List<Transaction>, hideDecimals: Boolean, onClick: () -> Unit,
    period: HomeSummaryPeriod? = null) {
    val summary = homeMonthActivity(transactions)
    val income = summary.incomeCents
    val spending = summary.expenseCents
    val net = income - spending
    val scale = maxOf(income, spending, 1L).toFloat()
    DashboardSectionHeader(stringResource(HomeSection.THIS_MONTH.titleRes), stringResource(R.string.home_view_activity), onClick)
    Surface(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal).fillMaxWidth()
        .clip(MaterialTheme.shapes.large).clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.lg)) {
            if (period != null) {
                Text("${formatDate(period.start)} – ${formatDate(period.endInclusive)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm).testTag("homeSummaryPeriod"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MonthMetric(stringResource(R.string.home_income), income, hideDecimals, MaterialTheme.colorScheme.success, Modifier.weight(1f))
                MonthMetric(stringResource(R.string.home_spent), spending, hideDecimals, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MonthMetric(stringResource(R.string.home_net), net, hideDecimals,
                    if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.lg))
            MonthBar(stringResource(R.string.home_income), income / scale, MaterialTheme.colorScheme.success)
            Spacer(Modifier.height(Spacing.sm))
            MonthBar(stringResource(R.string.home_spending), spending / scale, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.lg))
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Text(stringResource(R.string.home_activity), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "· " + pluralStringResource(R.plurals.home_transactions_count, transactions.size, transactions.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    DashboardSectionHeader(stringResource(HomeSection.REPORTS.titleRes), stringResource(R.string.home_view_all), onViewAllClick)
    DashboardSurface {
        if (reports.isEmpty()) {
            InsightRow(stringResource(R.string.home_dashboards), stringResource(R.string.home_explore_trends), onViewAllClick)
        } else {
            reports.take(5).forEachIndexed { index, report ->
                InsightRow(report.name, stringResource(R.string.home_open_dashboard)) { onReportClick(report.id) }
                if (index != reports.take(5).lastIndex) DashboardDivider()
            }
            DashboardFooter(stringResource(R.string.home_view_all_reports), Icons.Outlined.BarChart, onViewAllClick)
        }
    }
}

@Composable
private fun InsightRow(title: String, subtitle: String, onClick: () -> Unit) {
    ActuaFormRow(icon = Icons.Outlined.BarChart, label = title, value = null, caption = subtitle, onClick = onClick)
}

@Composable
private fun RecentActivitySection(transactions: List<Transaction>, hideDecimals: Boolean,
    onClick: () -> Unit) {
    DashboardSectionHeader(stringResource(HomeSection.RECENT_ACTIVITY.titleRes), stringResource(R.string.home_view_all), onClick)
    if (transactions.isEmpty()) {
        DashboardEmptyCard(stringResource(R.string.home_no_recent_activity), onClick)
        return
    }
    DashboardSurface {
        transactions.take(5).forEachIndexed { index, transaction ->
            ActivityRow(transaction, hideDecimals, onClick)
            if (index != transactions.take(5).lastIndex) DashboardDivider()
        }
        DashboardFooter(stringResource(R.string.home_view_all_activity), Icons.AutoMirrored.Outlined.ReceiptLong, onClick)
    }
}

@Composable
private fun ActivityRow(transaction: Transaction, hideDecimals: Boolean, onClick: () -> Unit) {
    DashboardRow(onClick) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(transaction.payee.ifBlank { "T" }.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(transaction.payee.ifBlank { stringResource(R.string.home_transaction) }, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(transaction.category.ifBlank { transaction.account }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text(formatMoneyCents(transaction.amountCents, hideDecimals), style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A group label, as on Add transaction, with the section's shortcut on the right. */
@Composable
private fun DashboardSectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
        verticalAlignment = Alignment.Bottom) {
        ActuaGroupLabel(title, Modifier.weight(1f))
        Text(action, modifier = Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** The shared form-card surface, inset to the screen gutter. */
@Composable
private fun DashboardSurface(content: @Composable ColumnScope.() -> Unit) {
    ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal), content = content)
}

/** A tappable list row inside a [DashboardSurface], at the compact row height. */
@Composable
private fun DashboardRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight).clickable(onClick = onClick)
        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun DashboardEmptyCard(label: String, onClick: () -> Unit) {
    DashboardSurface {
        DashboardRow(onClick) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardFooter(label: String, icon: ImageVector, onClick: () -> Unit) {
    DashboardDivider()
    DashboardRow(onClick) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DashboardDivider() {
    ActuaCardDivider(inset = Spacing.lg)
}

@Composable
private fun categoryProgressColor(category: BudgetCategory): Color =
    categoryStatusColor(category.progressBarState())

@Composable
private fun categoryProgressLabel(category: BudgetCategory, percent: Int): String =
    if (category.usesGoalProgress) stringResource(R.string.home_goal_progress, percent)
    else stringResource(R.string.home_spending_progress, stringResource(category.progressState.labelRes), percent)

/** An account type's display name; unknown tokens fall back to the capitalized token itself. */
@Composable
private fun accountTypeLabel(type: String): String = when (type) {
    "checking" -> stringResource(R.string.home_account_checking)
    "savings" -> stringResource(R.string.home_account_savings)
    "credit" -> stringResource(R.string.home_account_credit)
    "investment" -> stringResource(R.string.home_account_investment)
    "mortgage" -> stringResource(R.string.home_account_mortgage)
    "debt" -> stringResource(R.string.home_account_debt)
    else -> type.replaceFirstChar { it.uppercase() }
}

@Composable
private fun scheduleStatusColor(status: ScheduleStatus): Color = when (status) {
    ScheduleStatus.MISSED -> MaterialTheme.colorScheme.error
    ScheduleStatus.DUE -> MaterialTheme.colorScheme.warning
    ScheduleStatus.UPCOMING -> MaterialTheme.colorScheme.primary
    ScheduleStatus.PAID, ScheduleStatus.COMPLETED -> MaterialTheme.colorScheme.success
    ScheduleStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun scheduleLabel(item: ScheduleListItem): String {
    val due = item.schedule.nextDate?.let { date ->
        when (DayDate.today().daysUntil(date)) {
            in Int.MIN_VALUE..-1 -> stringResource(R.string.home_overdue)
            0 -> stringResource(R.string.home_due_today)
            1 -> stringResource(R.string.home_due_tomorrow)
            else -> stringResource(R.string.home_due_date, date.iso)
        }
    } ?: stringResource(R.string.home_scheduled)
    return listOfNotNull(due, item.accountName?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
