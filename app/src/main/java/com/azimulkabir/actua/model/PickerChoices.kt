package com.azimulkabir.actua.model

/**
 * A picker entry: [label] is unique even when Actual lets several rows share [name]. [group] is
 * the category group a category is listed under. A choice that isn't [offered] (a hidden
 * category, or one in a hidden group) is never listed but still resolves, so a transaction that
 * already uses it keeps it when edited.
 */
data class PickerChoice(
    val id: String,
    val name: String,
    val label: String,
    val group: String? = null,
    val offered: Boolean = true,
)

/**
 * Account or category choices for string-valued pickers. Actual allows duplicate account names
 * and the same category name in different groups, so a picked label maps back to one id.
 */
class PickerChoices(val choices: List<PickerChoice>) {
    /** The labels a picker offers, in display order. */
    val labels: List<String> = choices.filter { it.offered }.map { it.label }

    /** Offered labels under their group headings, in display order; empty when nothing is grouped. */
    val sections: List<Pair<String, List<String>>> = buildList {
        choices.forEach { choice ->
            val group = choice.group
            if (!choice.offered || group == null) return@forEach
            val last = lastOrNull()
            if (last != null && last.first == group) {
                set(lastIndex, group to last.second + choice.label)
            } else {
                add(group to listOf(choice.label))
            }
        }
    }
    private val byLabel = choices.associateBy { it.label }
    private val byId = choices.associateBy { it.id }

    fun choice(label: String): PickerChoice? = byLabel[label]

    /** The label for a row: by id when known, else the first choice with [name] (offered first), else [name]. */
    fun labelOf(id: String?, name: String): String =
        id?.let(byId::get)?.label
            ?: (choices.firstOrNull { it.offered && it.name == name } ?: choices.firstOrNull { it.name == name })?.label
            ?: name

    companion object {
        val EMPTY = PickerChoices(emptyList())

        /** Accounts as (id, name) in display order; a shared name is numbered in that order. */
        fun accounts(accounts: List<Pair<String, String>>): PickerChoices {
            val counts = accounts.groupingBy { it.second }.eachCount()
            val seen = mutableMapOf<String, Int>()
            return build(accounts.map { (id, name) ->
                val number = (seen[name] ?: 0) + 1
                seen[name] = number
                PickerChoice(id, name, if (counts.getValue(name) > 1) "$name ($number)" else name)
            })
        }

        /**
         * Categories as (group name, (id, name)) in display order; a shared name shows its group.
         * [hiddenIds] are kept for resolving existing rows but not offered, and an offered name
         * only shows its group when another offered category shares it.
         */
        fun categories(
            categories: List<Pair<String, Pair<String, String>>>,
            hiddenIds: Set<String> = emptySet(),
        ): PickerChoices {
            val allCounts = categories.groupingBy { it.second.second }.eachCount()
            val offeredCounts = categories.filterNot { it.second.first in hiddenIds }
                .groupingBy { it.second.second }.eachCount()
            return build(categories.map { (group, category) ->
                val (id, name) = category
                val offered = id !in hiddenIds
                val shared = (if (offered) offeredCounts else allCounts).getValue(name) > 1
                PickerChoice(id, name, if (shared) "$name ($group)" else name, group, offered)
            })
        }

        private fun build(rows: List<PickerChoice>): PickerChoices {
            val taken = mutableSetOf<String>()
            // Offered rows claim their labels first, so a hidden row never renames a listed one.
            val labels = arrayOfNulls<String>(rows.size)
            (rows.indices.filter { rows[it].offered } + rows.indices.filterNot { rows[it].offered }).forEach { index ->
                // A real name can equal a generated label, e.g. an account named "Checking (2)".
                val label = rows[index].label
                var unique = label
                var suffix = 2
                while (!taken.add(unique)) unique = "$label (${suffix++})"
                labels[index] = unique
            }
            return PickerChoices(rows.mapIndexed { index, row -> row.copy(label = labels[index]!!) })
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
