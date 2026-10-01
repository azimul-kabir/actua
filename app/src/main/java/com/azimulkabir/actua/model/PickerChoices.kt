package com.azimulkabir.actua.model

/** A picker entry: [label] is unique even when Actual lets several rows share [name]. */
data class PickerChoice(val id: String, val name: String, val label: String)

/**
 * Account or category choices for string-valued pickers. Actual allows duplicate account names
 * and the same category name in different groups, so a picked label maps back to one id.
 */
class PickerChoices(val choices: List<PickerChoice>) {
    val labels: List<String> = choices.map { it.label }
    private val byLabel = choices.associateBy { it.label }
    private val byId = choices.associateBy { it.id }

    fun choice(label: String): PickerChoice? = byLabel[label]

    /** The label for a row: by id when known, else the first choice with [name], else [name]. */
    fun labelOf(id: String?, name: String): String =
        id?.let(byId::get)?.label ?: choices.firstOrNull { it.name == name }?.label ?: name

    companion object {
        val EMPTY = PickerChoices(emptyList())

        /** Accounts as (id, name) in display order; a shared name is numbered in that order. */
        fun accounts(accounts: List<Pair<String, String>>): PickerChoices {
            val counts = accounts.groupingBy { it.second }.eachCount()
            val seen = mutableMapOf<String, Int>()
            return build(accounts.map { (id, name) ->
                val number = (seen[name] ?: 0) + 1
                seen[name] = number
                Triple(id, name, if (counts.getValue(name) > 1) "$name ($number)" else name)
            })
        }

        /** Categories as (group name, (id, name)) in display order; a shared name shows its group. */
        fun categories(categories: List<Pair<String, Pair<String, String>>>): PickerChoices {
            val counts = categories.groupingBy { it.second.second }.eachCount()
            return build(categories.map { (group, category) ->
                val (id, name) = category
                Triple(id, name, if (counts.getValue(name) > 1) "$name ($group)" else name)
            })
        }

        private fun build(rows: List<Triple<String, String, String>>): PickerChoices {
            val taken = mutableSetOf<String>()
            return PickerChoices(rows.map { (id, name, label) ->
                // A real name can equal a generated label, e.g. an account named "Checking (2)".
                var unique = label
                var suffix = 2
                while (!taken.add(unique)) unique = "$label (${suffix++})"
                PickerChoice(id, name, unique)
            })
        }
    }
}

/** This row with its account and category names replaced by picker labels, ids kept. */
fun Transaction.withChoiceLabels(accounts: PickerChoices, categories: PickerChoices): Transaction = copy(
    account = accounts.labelOf(accountId, account),
    transferAccount = transferAccount?.let { accounts.labelOf(transferAccountId, it) },
    category = categories.labelOf(categoryId, category),
    splits = splits.map { it.copy(category = categories.labelOf(it.categoryId, it.category)) },
)

/**
 * A form draft with picker labels swapped for the names and ids they stand for. Ids always come
 * from the labels, so an id left over from before an edit never outlives a changed choice.
 */
fun Transaction.resolveChoices(accounts: PickerChoices, categories: PickerChoices): Transaction {
    val account = accounts.choice(account)
    val transfer = transferAccount?.let(accounts::choice)
    val category = categories.choice(category)
    return copy(
        account = account?.name ?: this.account,
        accountId = account?.id,
        transferAccount = transfer?.name ?: transferAccount,
        transferAccountId = transfer?.id,
        category = category?.name ?: this.category,
        categoryId = category?.id,
        splits = splits.map { line ->
            val lineCategory = categories.choice(line.category)
            line.copy(category = lineCategory?.name ?: line.category, categoryId = lineCategory?.id)
        },
    )
}

/**
 * The row a save means: the one with [id] while it still has [name], else the first one named
 * [name]. The name check keeps a stale id, from a copy that changed only the name, from winning.
 */
fun <T> List<T>.byIdOrName(id: String?, name: String, idOf: (T) -> String, nameOf: (T) -> String): T? =
    id?.let { firstOrNull { row -> idOf(row) == it && nameOf(row) == name } } ?: firstOrNull { nameOf(it) == name }
