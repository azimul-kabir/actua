package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.reports.SavedReportEngine
import com.azimulkabir.actua.data.reports.SavedReportRow
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.reports.charts.ChartCard
import com.azimulkabir.actua.ui.settings.ConditionEditor
import com.azimulkabir.actua.ui.settings.defaultValue
import com.azimulkabir.actua.ui.theme.Spacing

private val DatePresets = listOf(
    "This month", "Last month", "This week", "Last week",
    "Current quarter", "Previous quarter", "Last 30 days",
    "Last 3 months", "Last 6 months", "Last 12 months",
    "Year to date", "Last year", "Prior year to date", "All time",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReportBuilderScreen(
    initialReport: SavedReportRow?,
    editorData: RuleEditorData,
    onPreview: (SavedReportRow) -> ReportWidget?,
    onSave: (SavedReportRow) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialReport?.name.orEmpty()) }
    var graphType by rememberSaveable { mutableStateOf(initialReport?.graphType ?: "BarGraph") }
    var balanceType by rememberSaveable { mutableStateOf(initialReport?.balanceType ?: "Payment") }
    var groupBy by rememberSaveable { mutableStateOf(initialReport?.groupBy ?: "Category") }
    var barMode by rememberSaveable { mutableStateOf(if (initialReport?.mode == "time" || initialReport?.groupBy == "Interval") "time" else "total") }
    var interval by rememberSaveable { mutableStateOf(initialReport?.interval ?: "Monthly") }
    var dateStatic by rememberSaveable { mutableStateOf(initialReport?.dateStatic ?: false) }
    var dateRange by rememberSaveable { mutableStateOf(initialReport?.dateRange ?: "Last 3 months") }
    var includeCurrent by rememberSaveable { mutableStateOf(initialReport?.includeCurrent ?: true) }
    var startDate by rememberSaveable { mutableStateOf(initialReport?.startDate.orEmpty()) }
    var endDate by rememberSaveable { mutableStateOf(initialReport?.endDate.orEmpty()) }

    var showOffBudget by rememberSaveable { mutableStateOf(initialReport?.showOffBudget ?: false) }
    var showHidden by rememberSaveable { mutableStateOf(initialReport?.showHidden ?: false) }
    var showUncategorized by rememberSaveable { mutableStateOf(initialReport?.showUncategorized ?: true) }
    var showEmpty by rememberSaveable { mutableStateOf(initialReport?.showEmpty ?: false) }
    var trimIntervals by rememberSaveable { mutableStateOf(initialReport?.trimIntervals ?: false) }
    var sortBy by rememberSaveable { mutableStateOf(initialReport?.sortBy ?: "desc") }

    val initialParsed = remember(initialReport) {
        if (initialReport != null) SavedReportEngine.parseConditions(initialReport)
        else emptyList<Rule.Condition>() to Rule.ConditionsOp.AND
    }
    var conditions by remember { mutableStateOf(initialParsed.first) }
    var conditionsOp by remember { mutableStateOf(initialParsed.second) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val effectiveMode = when (graphType) {
        "LineGraph", "AreaGraph", "StackedBarGraph" -> "time"
        "DonutGraph" -> "total"
        else -> if (groupBy == "Interval") "time" else barMode
    }

    val currentConditionsJson = remember(conditions) {
        Rule("", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, conditions, emptyList()).conditionsJson
    }

    val draftReport = remember(
        name, graphType, balanceType, groupBy, effectiveMode, interval,
        dateStatic, dateRange, includeCurrent, startDate, endDate,
        currentConditionsJson, conditionsOp, showOffBudget, showHidden,
        showUncategorized, showEmpty, trimIntervals, sortBy,
    ) {
        SavedReportRow(
            id = initialReport?.id.orEmpty(),
            name = name.ifBlank { "Untitled report" },
            startDate = startDate.ifBlank { null },
            endDate = endDate.ifBlank { null },
            dateStatic = dateStatic,
            dateRange = dateRange,
            groupBy = groupBy,
            balanceType = balanceType,
            showOffBudget = showOffBudget,
            showHidden = showHidden,
            showUncategorized = showUncategorized,
            selectedCategories = null,
            graphType = graphType,
            conditions = currentConditionsJson,
            conditionsOp = if (conditionsOp == Rule.ConditionsOp.OR) "or" else "and",
            interval = interval,
            mode = effectiveMode,
            includeCurrent = includeCurrent,
            sortBy = sortBy,
            showEmpty = showEmpty,
            trimIntervals = trimIntervals,
        )
    }

    val previewWidget = remember(draftReport) { onPreview(draftReport) }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(
            title = if (initialReport == null) "New Custom Report" else "Edit Custom Report",
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = { onSave(draftReport) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("saveCustomReportHeaderButton"),
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Live Preview Card
            if (previewWidget != null) {
                ActuaGroupLabel("Preview")
                val header = calculateWidgetHeader(previewWidget, hideDecimals = false)
                ChartCard(
                    title = draftReport.name,
                    headlineValue = header.headlineValue,
                    caption = header.caption,
                    trend = header.trend,
                    modifier = Modifier.testTag("customReportPreviewCard"),
                ) {
                    WidgetBody(
                        widget = previewWidget,
                        hideDecimals = false,
                        showSummary = false,
                        onShowSummaryChange = {},
                        onDrillDown = {},
                    )
                }
            }

            // Report Name
            ActuaGroupLabel("Report details")
            ActuaFormCard {
                ActuaFormTextField(
                    icon = Icons.Outlined.Edit,
                    label = "Report name",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.testTag("customReportNameField"),
                )
            }

            // Graph Type
            ActuaGroupLabel("Chart type")
            val graphOptions = listOf(
                "BarGraph" to "Bar",
                "DonutGraph" to "Donut",
                "StackedBarGraph" to "Stacked",
                "LineGraph" to "Line",
                "AreaGraph" to "Area",
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                graphOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = graphType == value,
                        onClick = { graphType = value },
                        shape = SegmentedButtonDefaults.itemShape(index, graphOptions.size),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Balance Type & Grouping
            ActuaGroupLabel("Data & Grouping")
            ActuaFormCard {
                val balanceOptions = listOf(
                    "Expense" to "Payment",
                    "Income" to "Deposit",
                    "Net" to "Net",
                    "Budgeted" to "Budgeted",
                )
                ActuaMenuRow(
                    icon = Icons.Outlined.Paid,
                    label = "Balance type",
                    value = balanceOptions.firstOrNull { it.second == balanceType }?.first ?: balanceType,
                    choices = balanceOptions.map { it.first to it.second },
                ) { balanceType = it }

                ActuaCardDivider()

                val groupOptions = listOf(
                    "Category" to "Category",
                    "Category Group" to "Group",
                    "Payee" to "Payee",
                    "Account" to "Account",
                    "Interval" to "Interval",
                )
                ActuaMenuRow(
                    icon = Icons.Outlined.Category,
                    label = "Group by",
                    value = groupOptions.firstOrNull { it.second == groupBy }?.first ?: groupBy,
                    choices = groupOptions.map { it.first to it.second },
                ) { groupBy = it }

                if (graphType == "BarGraph" && groupBy != "Interval") {
                    ActuaCardDivider()
                    val modeOptions = listOf("Group totals" to "total", "Over time" to "time")
                    ActuaMenuRow(
                        icon = Icons.Outlined.BarChart,
                        label = "Bar layout",
                        value = modeOptions.firstOrNull { it.second == barMode }?.first ?: barMode,
                        choices = modeOptions.map { it.first to it.second },
                    ) { barMode = it }
                }

                if (effectiveMode == "time" || graphType in listOf("LineGraph", "AreaGraph", "StackedBarGraph")) {
                    ActuaCardDivider()
                    val intervalOptions = listOf("Monthly", "Weekly", "Daily", "Yearly")
                    ActuaMenuRow(
                        icon = Icons.Outlined.Timeline,
                        label = "Interval",
                        value = interval,
                        choices = intervalOptions.map { it to it },
                    ) { interval = it }
                }
            }

            // Time frame
            ActuaGroupLabel("Date range")
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.DateRange,
                    label = "Custom date range",
                    value = null,
                    checked = dateStatic,
                    onClick = { dateStatic = !dateStatic },
                )
                ActuaCardDivider()
                if (!dateStatic) {
                    ActuaMenuRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Range",
                        value = dateRange,
                        choices = DatePresets.map { it to it },
                    ) { dateRange = it }
                    if (dateRange.startsWith("Last ")) {
                        ActuaCardDivider()
                        ActuaFormRow(
                            icon = Icons.Outlined.Visibility,
                            label = "Include current period",
                            value = null,
                            checked = includeCurrent,
                            onClick = { includeCurrent = !includeCurrent },
                        )
                    }
                } else {
                    ActuaFormTextField(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Start date (YYYY-MM-DD)",
                        value = startDate,
                        onValueChange = { startDate = it },
                    )
                    ActuaCardDivider()
                    ActuaFormTextField(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "End date (YYYY-MM-DD)",
                        value = endDate,
                        onValueChange = { endDate = it },
                    )
                }
            }

            // Filter conditions
            ActuaGroupLabel("Filter conditions")
            if (conditions.isNotEmpty()) {
                val opChoices = listOf(
                    Rule.ConditionsOp.AND to "All conditions must match",
                    Rule.ConditionsOp.OR to "Any condition can match",
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opChoices.forEachIndexed { index, (op, label) ->
                        SegmentedButton(
                            selected = conditionsOp == op,
                            onClick = { conditionsOp = op },
                            shape = SegmentedButtonDefaults.itemShape(index, opChoices.size),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            conditions.forEachIndexed { index, condition ->
                ConditionEditor(
                    index = index,
                    condition = condition,
                    data = editorData,
                    onChange = { updated ->
                        conditions = conditions.toMutableList().also { it[index] = updated }
                    },
                    onRemove = {
                        conditions = conditions.toMutableList().also { it.removeAt(index) }
                    },
                )
            }

            ActuaSecondaryButton(
                icon = Icons.Outlined.AddCircleOutline,
                text = "Add condition",
                onClick = {
                    conditions = conditions + Rule.Condition("is", "category", defaultValue("category", "is"))
                },
                modifier = Modifier.fillMaxWidth().testTag("addConditionButton"),
            )

            // Display Options
            ActuaGroupLabel("Display options")
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Visibility,
                    label = "Off-budget accounts",
                    value = null,
                    checked = showOffBudget,
                    onClick = { showOffBudget = !showOffBudget },
                )
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.Visibility,
                    label = "Hidden categories",
                    value = null,
                    checked = showHidden,
                    onClick = { showHidden = !showHidden },
                )
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.Visibility,
                    label = "Uncategorized",
                    value = null,
                    checked = showUncategorized,
                    onClick = { showUncategorized = !showUncategorized },
                )
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.Visibility,
                    label = "Show empty groups",
                    value = null,
                    checked = showEmpty,
                    onClick = { showEmpty = !showEmpty },
                )
                if (effectiveMode == "time") {
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Tune,
                        label = "Trim empty intervals",
                        value = null,
                        checked = trimIntervals,
                        onClick = { trimIntervals = !trimIntervals },
                    )
                }
                ActuaCardDivider()
                val sortChoices = listOf(
                    "Descending" to "desc",
                    "Ascending" to "asc",
                    "Name" to "name",
                    "Item order" to "budget",
                )
                ActuaMenuRow(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    label = "Sort order",
                    value = sortChoices.firstOrNull { it.second == sortBy }?.first ?: sortBy,
                    choices = sortChoices.map { it.first to it.second },
                ) { sortBy = it }
            }

            // Delete Custom Report (if editing)
            if (initialReport != null && onDelete != null) {
                Spacer(Modifier.height(Spacing.md))
                ActuaSecondaryButton(
                    icon = Icons.Outlined.Delete,
                    text = "Delete custom report",
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().testTag("deleteCustomReportButton"),
                )
            }
        }

        ActuaPrimaryActionBar(
            text = if (initialReport == null) "Create report" else "Save changes",
            onClick = { onSave(draftReport) },
            enabled = name.isNotBlank(),
            modifier = Modifier.testTag("saveCustomReportBottomBar"),
        )
    }

    if (showDeleteConfirm && initialReport != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Report?") },
            text = { Text("Are you sure you want to delete \"${initialReport.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(initialReport.id)
                        onBack()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
