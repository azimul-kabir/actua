from pathlib import Path

path = Path("app/src/main/java/com/azimulkabir/actua/ui/budget/BudgetScreen.kt")
text = path.read_text()

def replace(old: str, new: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"Expected snippet not found:\n{old[:300]}")
    text = text.replace(old, new, 1)

replace(
    '            detail = category.automations.joinToString { it.type.label }\n',
    '            detail = category.automations.joinToString { if (it.isBalanceCap) "Balance cap" else it.type.label }\n',
)
replace(
    '''            supporting = when (target.type) {
                BudgetTarget.Type.GOAL -> "$timing · Does not budget funds automatically"
''',
    '''            supporting = when {
                target.isBalanceCap -> "$timing · Does not request funding automatically"
                target.type == BudgetTarget.Type.GOAL -> "$timing · Does not budget funds automatically"
''',
)
replace(
    '''                BudgetTarget.Type.REMAINDER -> "$timing · Applied in whole-budget preview"
                BudgetTarget.Type.PERCENTAGE -> "$timing · Applied in whole-budget preview"
                BudgetTarget.Type.SCHEDULE -> "$timing · Read-only until schedule evaluation is exact"
                else -> "$timing · Auto-Assign ${formatMoneyCents(target.suggestedBudget(category, month), hideDecimalPlaces)}"
''',
    '''                target.type == BudgetTarget.Type.REMAINDER -> "$timing · Applied in whole-budget preview"
                target.type == BudgetTarget.Type.PERCENTAGE -> "$timing · Applied in whole-budget preview"
                target.type == BudgetTarget.Type.SCHEDULE -> "$timing · Read-only until schedule evaluation is exact"
                else -> "$timing · Auto-Assign ${formatMoneyCents(target.suggestedBudget(category, month), hideDecimalPlaces)}"
''',
)
replace(
    '''    category.target?.let { target ->
        add("Target · ${target.type.label}" to target.suggestedBudget(category, month))
    }
''',
    '''    category.target?.takeUnless(BudgetTarget::isBalanceCap)?.let { target ->
        add("Target · ${target.type.label}" to target.suggestedBudget(category, month))
    }
''',
)
replace(
    '''            Text(type.explanation, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
''',
    '''            Text(
                if (type == BudgetTarget.Type.REFILL && editingBalanceCap)
                    "Limit the category balance without requesting refill funding."
                else type.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
''',
)
path.write_text(text)
