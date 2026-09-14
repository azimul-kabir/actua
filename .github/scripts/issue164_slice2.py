from pathlib import Path

path = Path("app/src/main/java/com/azimulkabir/actua/ui/budget/BudgetScreen.kt")
text = path.read_text()

if 'Text("Balance cap cadence"' in text:
    raise SystemExit(0)

def replace(old: str, new: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"Expected snippet not found:\n{old[:300]}")
    text = text.replace(old, new, 1)

replace(
    '            title = target.type.label\n',
    '            title = if (target.isBalanceCap) "Balance cap" else target.type.label\n',
)
replace(
    '                BudgetTarget.Type.MONTHLY_SPENDING, BudgetTarget.Type.MONTHLY_SAVINGS,\n                BudgetTarget.Type.REFILL -> "Resets every month"\n',
    '''                BudgetTarget.Type.MONTHLY_SPENDING, BudgetTarget.Type.MONTHLY_SAVINGS -> "Resets every month"
                BudgetTarget.Type.REFILL -> if (target.isBalanceCap) {
                    when (target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY) {
                        BudgetTarget.LimitPeriod.DAILY -> "Daily balance cap"
                        BudgetTarget.LimitPeriod.WEEKLY -> "Weekly balance cap"
                        BudgetTarget.LimitPeriod.MONTHLY -> "Monthly balance cap"
                    }
                } else "Resets every month"
''',
)
replace(
    '    var limitPeriod by remember(category) { mutableStateOf(category.target?.limitPeriod) }\n',
    '''    var editingBalanceCap by remember(category) { mutableStateOf(category.target?.isBalanceCap == true) }
    var limitPeriod by remember(category) {
        mutableStateOf(category.target?.limitPeriod ?: if (category.target?.isBalanceCap == true)
            BudgetTarget.LimitPeriod.MONTHLY else null)
    }
''',
)
replace(
    '        BudgetTarget.Type.WEEKLY_SPENDING -> runCatching { java.time.LocalDate.parse(date) }.isSuccess\n        else -> true\n',
    '''        BudgetTarget.Type.WEEKLY_SPENDING -> runCatching { java.time.LocalDate.parse(date) }.isSuccess
        BudgetTarget.Type.REFILL -> !editingBalanceCap || limitPeriod != BudgetTarget.LimitPeriod.WEEKLY ||
            runCatching { java.time.LocalDate.parse(limitStartDate) }.isSuccess
        else -> true
''',
)
replace(
    '        BudgetTarget.Type.COPY -> lookBackMonths.toIntOrNull() in 1..24\n        BudgetTarget.Type.REMAINDER -> weight.toIntOrNull()?.let { it >= 1 } == true &&\n',
    '''        BudgetTarget.Type.COPY -> lookBackMonths.toIntOrNull() in 1..24
        BudgetTarget.Type.REFILL -> amountCents?.let { it > 0L } == true &&
            (!editingBalanceCap || limitPeriod != null)
        BudgetTarget.Type.REMAINDER -> weight.toIntOrNull()?.let { it >= 1 } == true &&
''',
)
replace(
    '                            Text(type.label, fontWeight = FontWeight.SemiBold)\n',
    '''                            Text(if (editingBalanceCap && type == BudgetTarget.Type.REFILL) "Balance cap" else type.label,
                                fontWeight = FontWeight.SemiBold)
''',
)
replace(
    '                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {\n                    BudgetTarget.Type.entries.filterNot { it == BudgetTarget.Type.SCHEDULE }.forEach { option ->\n',
    '''                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    DropdownMenuItem(text = {
                        Column {
                            Text("Balance cap")
                            Text("Limit the category balance without requesting refill funding",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = {
                        type = BudgetTarget.Type.REFILL
                        editingBalanceCap = true
                        if (limitPeriod == null) limitPeriod = BudgetTarget.LimitPeriod.MONTHLY
                        typeMenu = false
                    })
                    BudgetTarget.Type.entries.filterNot { it == BudgetTarget.Type.SCHEDULE }.forEach { option ->
''',
)
replace(
    '                        }, onClick = { type = option; typeMenu = false })\n',
    '                        }, onClick = { type = option; editingBalanceCap = false; typeMenu = false })\n',
)
replace(
    '            if (type == BudgetTarget.Type.REMAINDER) {\n',
    '''            if (type == BudgetTarget.Type.REFILL && editingBalanceCap) {
                Text("Balance cap cadence", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetTarget.LimitPeriod.entries.forEach { period ->
                        FilledTonalButton(
                            onClick = { limitPeriod = period },
                            modifier = Modifier.weight(1f),
                            colors = if (limitPeriod == period) ButtonDefaults.filledTonalButtonColors()
                                else ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                        ) { Text(period.jsonValue.replaceFirstChar(Char::uppercase)) }
                    }
                }
                if (limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) {
                    OutlinedTextField(
                        value = limitStartDate,
                        onValueChange = { limitStartDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Weekly start date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Retain existing funds over the cap")
                        Text(
                            if (limitHold) "Excess carryover stays in the category."
                            else "Excess carryover is released to Ready to Budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = limitHold, onCheckedChange = { limitHold = it })
                }
            }
            if (type == BudgetTarget.Type.REMAINDER) {
''',
)
replace(
    '                        BudgetTarget.Type.MONTHLY_SPENDING, BudgetTarget.Type.MONTHLY_SAVINGS, BudgetTarget.Type.REFILL -> "Every month"\n',
    '''                        BudgetTarget.Type.MONTHLY_SPENDING, BudgetTarget.Type.MONTHLY_SAVINGS -> "Every month"
                        BudgetTarget.Type.REFILL -> if (editingBalanceCap)
                            (limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY).jsonValue.replaceFirstChar(Char::uppercase)
                            else "Every month"
''',
)
replace(
    '                        priority = category.target?.priority ?: 1,\n',
    '''                        priority = if (type == BudgetTarget.Type.REFILL && editingBalanceCap) 0
                            else category.target?.priority?.takeIf { it > 0 } ?: 1,
''',
)
replace(
    '                        limitPeriod = if (type == BudgetTarget.Type.REMAINDER) limitPeriod else null,\n                        limitAmountCents = if (type == BudgetTarget.Type.REMAINDER) limitAmountCents else null,\n                        limitStartDate = if (type == BudgetTarget.Type.REMAINDER &&\n                            limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) limitStartDate else null,\n                        limitHold = if (type == BudgetTarget.Type.REMAINDER) limitHold else false,\n',
    '''                        limitPeriod = if (type == BudgetTarget.Type.REMAINDER ||
                            type == BudgetTarget.Type.REFILL && editingBalanceCap) limitPeriod else null,
                        limitAmountCents = if (type == BudgetTarget.Type.REMAINDER) limitAmountCents else null,
                        limitStartDate = if ((type == BudgetTarget.Type.REMAINDER ||
                            type == BudgetTarget.Type.REFILL && editingBalanceCap) &&
                            limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) limitStartDate else null,
                        limitHold = if (type == BudgetTarget.Type.REMAINDER ||
                            type == BudgetTarget.Type.REFILL && editingBalanceCap) limitHold else false,
''',
)
replace(
    '                            Text(target.type.label, fontWeight = FontWeight.SemiBold)\n',
    '''                            Text(if (target.isBalanceCap) "Balance cap" else target.type.label,
                                fontWeight = FontWeight.SemiBold)
''',
)
replace(
    '                                    BudgetTarget.Type.PERCENTAGE -> "${target.percentage}% of available funds"\n                                    else -> formatMoneyCents(target.amountCents, hideDecimalPlaces)\n',
    '''                                    BudgetTarget.Type.PERCENTAGE -> "${target.percentage}% of available funds"
                                    BudgetTarget.Type.REFILL -> if (target.isBalanceCap) {
                                        val cadence = (target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY).jsonValue
                                        formatMoneyCents(target.amountCents, hideDecimalPlaces) + " · " + cadence +
                                            if (target.limitHold) " · retain excess" else " · release excess"
                                    } else formatMoneyCents(target.amountCents, hideDecimalPlaces)
                                    else -> formatMoneyCents(target.amountCents, hideDecimalPlaces)
''',
)

path.write_text(text)
