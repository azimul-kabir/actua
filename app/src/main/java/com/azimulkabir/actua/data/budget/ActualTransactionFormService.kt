package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class ActualTransactionType { EXPENSE, INCOME, TRANSFER }

data class ActualTransactionForm(
    val accountId: String,
    val type: ActualTransactionType,
    val amount: String,
    val payeeName: String = "",
    val transferToAccountId: String? = null,
    val categoryId: String? = null,
    val notes: String = "",
    val date: Int,
    val cleared: Boolean = false,
    val splits: List<ActualSplitLineForm> = emptyList(),
    val collapseSplit: Boolean = false,
    /** True when [categoryId] was explicitly picked by the user, so a matching rule must not overwrite it. */
    val categoryIsExplicit: Boolean = false,
)

data class ActualSplitLineForm(
    val childId: String? = null,
    val categoryId: String? = null,
    val amount: String,
    val isOpposite: Boolean = false,
    val notes: String = "",
    val payeeName: String = "",
)

data class ActualSplitPlanLine(
    val childId: String?,
    val categoryId: String?,
    val amountCents: Long,
    val notes: String?,
    val payeeName: String?,
)

sealed interface ActualTransactionFormPlan {
    data class Standard(val amountCents: Long) : ActualTransactionFormPlan
    data class Transfer(val toAccountId: String, val amountCents: Long) : ActualTransactionFormPlan
    data class Split(val amountCents: Long, val lines: List<ActualSplitPlanLine>) : ActualTransactionFormPlan
}

sealed class ActualTransactionFormException(message: String) : IllegalArgumentException(message) {
    data object InvalidAmount : ActualTransactionFormException("Enter a valid amount")
    data object MissingTransferDestination : ActualTransactionFormException("Select a destination account")
    data object TransferAccountsMatch : ActualTransactionFormException("Transfer accounts must be different")
    data object TransferPayeeMissing : ActualTransactionFormException("An account transfer payee is missing")
    data object TransferPartnerMissing : ActualTransactionFormException("The paired transfer transaction is missing")
    data object SplitNeedsTwoLines : ActualTransactionFormException("A split needs at least two lines")
    data object SplitAmountMismatch : ActualTransactionFormException("Split amounts must equal the transaction total")
    data object CannotConvertToSplit : ActualTransactionFormException("This transaction cannot be converted to a split")
    data object SplitLineTransfer : ActualTransactionFormException("A split line can't be a transfer; choose a payee")
}

/** Pure planning plus persistence routing ported from BudgetStore.saveTransaction. */
class ActualTransactionFormService(
    private val database: ActualBudgetDatabase,
    private val writer: ActualTransactionWriter,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun plan(form: ActualTransactionForm): ActualTransactionFormPlan {
        return planNormalized(enforceOffBudgetCategoryPolicy(form, offBudgetAccountIds()))
    }

    private fun planNormalized(form: ActualTransactionForm): ActualTransactionFormPlan {
        val unsigned = cents(form.amount) ?: throw ActualTransactionFormException.InvalidAmount
        if (unsigned < 0) throw ActualTransactionFormException.InvalidAmount
        return when (form.type) {
            ActualTransactionType.TRANSFER -> ActualTransactionFormPlan.Transfer(
                form.transferToAccountId ?: throw ActualTransactionFormException.MissingTransferDestination,
                unsigned,
            )
            ActualTransactionType.EXPENSE -> standardOrSplit(form, -unsigned, -1)
            ActualTransactionType.INCOME -> standardOrSplit(form, unsigned, 1)
        }
    }

    /** Returns the newly-created ordinary transaction id; edits/transfers/splits return null. */
    fun save(
        form: ActualTransactionForm,
        original: ActualTransaction? = null,
        applyRules: Boolean = true,
    ): String? {
        require(form.accountId.isNotBlank())
        val normalizedForm = enforceOffBudgetCategoryPolicy(form, offBudgetAccountIds())
        val notes = normalizedForm.notes.takeIf(String::isNotEmpty)
        return when (val plan = planNormalized(normalizedForm)) {
            is ActualTransactionFormPlan.Transfer -> {
                if (original == null) createTransfer(normalizedForm, plan, notes)
                else if (original.transferId == null) convertToTransfer(original, normalizedForm, plan, notes)
                else updateTransfer(original, normalizedForm, plan, notes)
                null
            }
            is ActualTransactionFormPlan.Split -> {
                if (original == null) createSplit(normalizedForm, plan, notes)
                else if (original.isParent) updateSplit(original, normalizedForm, plan, notes)
                else convertToSplit(original, normalizedForm, plan, notes)
                null
            }
            is ActualTransactionFormPlan.Standard -> {
                if (original?.isParent == true && normalizedForm.collapseSplit) {
                    collapseSplit(original, normalizedForm, plan.amountCents, notes)
                    null
                } else if (original != null) {
                    val payee = resolvePayee(normalizedForm.payeeName, original)
                    // A transfer saved as an expense/income stops being a transfer (loot-core
                    // `transfer.onUpdate` → `removeTransfer`).
                    val detached = writer.detachTransfers(listOf(original))
                    mutate(
                        updates = listOf(original to original.copy(
                            accountId = normalizedForm.accountId,
                            date = normalizedForm.date,
                            amountCents = if (original.isParent) original.amountCents else plan.amountCents,
                            payeeId = payee?.id,
                            categoryId = if (original.isParent) null else normalizedForm.categoryId,
                            notes = notes,
                            cleared = normalizedForm.cleared,
                            transferId = null,
                        )) + detached.updates.filterNot { it.first.id == original.id },
                        tombstoneIds = detached.tombstoneIds,
                    )
                    null
                } else {
                    val payee = resolvePayee(normalizedForm.payeeName, null)
                    val id = idFactory()
                    writer.createTransaction(baseTransaction(
                        id, normalizedForm.accountId, normalizedForm.date, plan.amountCents, payee?.id,
                        normalizedForm.categoryId, notes, normalizedForm.cleared, importedPayee = payee?.name,
                    ), applyRules = applyRules, preserveCategory = normalizedForm.categoryIsExplicit && normalizedForm.categoryId != null)
                    id
                }
            }
        }
    }

    /** Every form update goes through [keepReconciledInvariant] (#746). */
    private fun mutate(
        updates: List<Pair<ActualTransaction, ActualTransaction>> = emptyList(),
        inserts: List<ActualTransaction> = emptyList(),
        tombstoneIds: List<String> = emptyList(),
    ) = writer.mutate(
        updates.map { (original, updated) -> original to keepReconciledInvariant(original, updated) },
        inserts,
        tombstoneIds,
    )

    private fun offBudgetAccountIds(): Set<String> = database.fetchAccounts()
        .filter { it.offBudget }
        .mapTo(mutableSetOf()) { it.id }

    private fun standardOrSplit(form: ActualTransactionForm, amount: Long, sign: Int): ActualTransactionFormPlan {
        if (form.splits.isEmpty()) return ActualTransactionFormPlan.Standard(amount)
        if (form.splits.size < 2) throw ActualTransactionFormException.SplitNeedsTwoLines
        val lines = form.splits.map { line ->
            val raw = cents(line.amount)?.takeIf { it > 0 } ?: throw ActualTransactionFormException.InvalidAmount
            ActualSplitPlanLine(
                line.childId, line.categoryId,
                sign * if (line.isOpposite) -raw else raw,
                line.notes.takeIf(String::isNotEmpty),
                line.payeeName.trim().takeIf(String::isNotEmpty),
            )
        }
        if (lines.sumOf(ActualSplitPlanLine::amountCents) != amount) {
            throw ActualTransactionFormException.SplitAmountMismatch
        }
        return ActualTransactionFormPlan.Split(amount, lines)
    }

    private fun createTransfer(form: ActualTransactionForm, plan: ActualTransactionFormPlan.Transfer, notes: String?) {
        if (form.accountId == plan.toAccountId) throw ActualTransactionFormException.TransferAccountsMatch
        val fromPayee = transferPayee(form.accountId)
        val toPayee = transferPayee(plan.toAccountId)
        val sourceId = idFactory()
        val targetId = idFactory()
        // loot-core `clearCategory`: only the on-budget leg of an on/off-budget transfer keeps a category.
        val offBudget = offBudgetAccountIds()
        fun category(account: String, other: String) =
            form.categoryId.takeIf { account !in offBudget && other in offBudget }
        // loot-core `addTransfer` inserts the other leg with `cleared: false`.
        writer.createTransfer(
            baseTransaction(sourceId, form.accountId, form.date, -plan.amountCents, toPayee.id,
                category(form.accountId, plan.toAccountId), notes, form.cleared, transferId = targetId),
            baseTransaction(targetId, plan.toAccountId, form.date, plan.amountCents, fromPayee.id,
                category(plan.toAccountId, form.accountId), notes, false, transferId = sourceId),
        )
    }

    /**
     * loot-core `updateTransfer` copies only account, payee, notes, amount and schedule to the other
     * leg; its cleared/reconciled state is its own account's. The date follows only when the synced
     * `sync-transfer-date` preference is on (desktop register), together with a split-child leg's parent.
     */
    private fun updateTransfer(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Transfer, notes: String?) {
        if (form.accountId == plan.toAccountId) throw ActualTransactionFormException.TransferAccountsMatch
        val partner = original.transferId?.let(database::fetchTransactionRow)
            ?: throw ActualTransactionFormException.TransferPartnerMissing
        val syncDate = database.syncTransferDate()
        fun date(leg: ActualTransaction) = if (leg.id == original.id || syncDate) form.date else leg.date
        fun cleared(leg: ActualTransaction) = if (leg.id == original.id) form.cleared else leg.cleared
        val sourceOriginal = if (original.amountCents < 0) original else partner
        val targetOriginal = if (original.amountCents < 0) partner else original
        val fromPayee = transferPayee(form.accountId)
        val toPayee = transferPayee(plan.toAccountId)
        val offBudget = database.fetchAccounts().filter { it.offBudget }.mapTo(mutableSetOf()) { it.id }
        fun category(leg: ActualTransaction, account: String, other: String): String? =
            if (account !in offBudget && other in offBudget) {
                if (leg.id == original.id) form.categoryId else leg.categoryId
            } else null
        val source = sourceOriginal.copy(
            accountId = form.accountId, date = date(sourceOriginal), amountCents = -plan.amountCents,
            payeeId = toPayee.id, categoryId = category(sourceOriginal, form.accountId, plan.toAccountId),
            notes = notes, cleared = cleared(sourceOriginal), scheduleId = original.scheduleId,
        )
        val target = targetOriginal.copy(
            accountId = plan.toAccountId, date = date(targetOriginal), amountCents = plan.amountCents,
            payeeId = fromPayee.id, categoryId = category(targetOriginal, plan.toAccountId, form.accountId),
            notes = notes, cleared = cleared(targetOriginal), scheduleId = original.scheduleId,
        )
        val partnerParent = partner.parentId
            ?.takeIf { syncDate && partner.date != form.date }
            ?.let(database::fetchTransaction)
            ?.let { it to it.copy(date = form.date) }
        mutate(updates = listOfNotNull(sourceOriginal to source, targetOriginal to target, partnerParent))
    }

    private fun convertToTransfer(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Transfer, notes: String?) {
        if (original.isParent || original.parentId != null) throw ActualTransactionFormException.CannotConvertToSplit
        if (form.accountId == plan.toAccountId) throw ActualTransactionFormException.TransferAccountsMatch
        val legPayee = transferPayee(form.accountId)
        val otherPayee = transferPayee(plan.toAccountId)
        val partnerId = idFactory()
        val signed = if (original.amountCents < 0) -plan.amountCents else plan.amountCents
        val offBudget = database.fetchAccounts().filter { it.offBudget }.mapTo(mutableSetOf()) { it.id }
        val legCategory = form.categoryId.takeIf { form.accountId !in offBudget && plan.toAccountId in offBudget }
        val leg = original.copy(
            accountId = form.accountId, date = form.date, amountCents = signed,
            payeeId = otherPayee.id, categoryId = legCategory, notes = notes,
            cleared = form.cleared, transferId = partnerId,
        )
        val partner = baseTransaction(
            partnerId, plan.toAccountId, form.date, -signed, legPayee.id, null, notes,
            false, transferId = original.id,
        )
        mutate(updates = listOf(original to leg), inserts = listOf(partner))
    }

    private fun createSplit(form: ActualTransactionForm, plan: ActualTransactionFormPlan.Split, notes: String?) {
        rejectTransferLines(plan)
        val parentPayee = resolvePayee(form.payeeName, null)
        val parentId = idFactory()
        val sort = nowMillis().toDouble()
        val parent = baseTransaction(
            parentId, form.accountId, form.date, plan.amountCents, parentPayee?.id,
            null, notes, form.cleared, isParent = true, sortOrder = sort,
            importedPayee = parentPayee?.name,
        )
        val children = plan.lines.mapIndexed { index, line ->
            val payee = resolveLinePayee(line, parentPayee, null)
            baseTransaction(
                idFactory(), form.accountId, form.date, line.amountCents, payee?.id,
                line.categoryId, line.notes, form.cleared, parentId = parentId,
                sortOrder = sort - index - 1,
            )
        }
        writer.createSplit(parent, children)
    }

    private fun updateSplit(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Split, notes: String?) {
        rejectTransferLines(plan)
        val parentPayee = resolvePayee(form.payeeName, original)
        val parent = original.copy(
            accountId = form.accountId, date = form.date, amountCents = plan.amountCents,
            payeeId = parentPayee?.id, categoryId = null, notes = notes, cleared = form.cleared,
        )
        val existing = database.fetchChildTransactions(original.id)
        val byId = existing.associateBy(ActualTransaction::id)
        val retained = mutableSetOf<String>()
        val updates = mutableListOf<Pair<ActualTransaction, ActualTransaction>>()
        val inserts = mutableListOf<ActualTransaction>()
        var nextSort = existing.mapNotNull(ActualTransaction::sortOrder).minOrNull()
            ?: original.sortOrder ?: nowMillis().toDouble()
        plan.lines.forEach { line ->
            val old = line.childId?.let(byId::get)
            val payee = resolveLinePayee(line, parentPayee, old)
            if (old != null) {
                retained += old.id
                updates += old to old.copy(
                    accountId = form.accountId, date = form.date, amountCents = line.amountCents,
                    payeeId = payee?.id, categoryId = line.categoryId, notes = line.notes,
                    cleared = form.cleared, parentId = original.id,
                )
            } else {
                nextSort -= 1
                inserts += baseTransaction(
                    idFactory(), form.accountId, form.date, line.amountCents, payee?.id,
                    line.categoryId, line.notes, form.cleared, parentId = original.id,
                    sortOrder = nextSort,
                )
            }
        }
        updates += original to parent
        val removed = existing.filterNot { it.id in retained }
        val removedIds = removed.mapTo(mutableSetOf(), ActualTransaction::id)
        val detached = writer.detachTransfers(removed, removedIds)
        mutate(updates + detached.updates, inserts, removedIds.toList() + detached.tombstoneIds)
    }

    private fun convertToSplit(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Split, notes: String?) {
        if (original.transferId != null || original.parentId != null) throw ActualTransactionFormException.CannotConvertToSplit
        rejectTransferLines(plan)
        val payee = resolvePayee(form.payeeName, original)
        val parent = original.copy(
            accountId = form.accountId, date = form.date, amountCents = plan.amountCents,
            payeeId = payee?.id, categoryId = null, notes = notes,
            cleared = form.cleared, isParent = true,
        )
        var nextSort = original.sortOrder ?: nowMillis().toDouble()
        val children = plan.lines.map { line ->
            nextSort -= 1
            val childPayee = resolveLinePayee(line, payee, null)
            baseTransaction(
                idFactory(), form.accountId, form.date, line.amountCents, childPayee?.id,
                line.categoryId, line.notes, form.cleared, parentId = original.id,
                sortOrder = nextSort,
            )
        }
        mutate(updates = listOf(original to parent), inserts = children)
    }

    private fun collapseSplit(original: ActualTransaction, form: ActualTransactionForm, amount: Long, notes: String?) {
        val payee = resolvePayee(form.payeeName, original)
        val updated = original.copy(
            accountId = form.accountId, date = form.date, amountCents = amount,
            payeeId = payee?.id, categoryId = form.categoryId, notes = notes,
            cleared = form.cleared, isParent = false,
        )
        val children = database.fetchChildTransactions(original.id)
        val childIds = children.mapTo(mutableSetOf(), ActualTransaction::id)
        val detached = writer.detachTransfers(children, childIds)
        mutate(
            updates = listOf(original to updated) + detached.updates,
            tombstoneIds = childIds.toList() + detached.tombstoneIds,
        )
    }

    private fun resolvePayee(name: String, original: ActualTransaction?): ActualPayee? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        if (clean == original?.payeeName) return original.payeeId?.let { id ->
            database.fetchPayees().firstOrNull { it.id == id }
        }
        return writer.resolveOrCreatePayee(clean)
    }

    private fun resolveLinePayee(line: ActualSplitPlanLine, parent: ActualPayee?, original: ActualTransaction?): ActualPayee? =
        line.payeeName?.takeIf { it != parent?.name }?.let { resolvePayee(it, original) } ?: parent

    /**
     * Split-line transfers aren't supported (#748), so a `Transfer: <account>` picker label must
     * never become an ordinary payee. Checked before anything is written; a payee that already
     * has that name still resolves, so rows saved before this check stay editable.
     */
    private fun rejectTransferLines(plan: ActualTransactionFormPlan.Split) {
        if (plan.lines.any { line ->
                line.payeeName?.let { isTransferLabel(it) && database.findPayeeByName(it.trim()) == null } == true
            }) throw ActualTransactionFormException.SplitLineTransfer
    }

    private fun transferPayee(accountId: String) = database.fetchPayees().firstOrNull {
        it.transferAccountId == accountId
    } ?: throw ActualTransactionFormException.TransferPayeeMissing

    private fun baseTransaction(
        id: String, account: String, date: Int, amount: Long, payee: String?, category: String?,
        notes: String?, cleared: Boolean, transferId: String? = null, isParent: Boolean = false,
        parentId: String? = null, sortOrder: Double? = null, importedPayee: String? = null,
    ) = ActualTransaction(
        id, account, date, amount, payee, null, category, null, notes, cleared,
        false, transferId, isParent, parentId, false, sortOrder, importedPayee,
        null, null,
    )

    companion object {
        /** The editor's `Transfer: <account>` payee label, which names an account, not a payee. */
        internal fun isTransferLabel(name: String): Boolean =
            name.trim().let { it.startsWith("Transfer: ") && it.length > "Transfer: ".length }

        /**
         * A reconciled row moved to another account is no longer reconciled there (desktop
         * `TransactionsTable` `onUpdate`); one that stays reconciled stays cleared, since the
         * mobile editor locks its Cleared toggle (`TransactionEdit.tsx`, Actual 59fe126f).
         */
        internal fun keepReconciledInvariant(original: ActualTransaction, updated: ActualTransaction): ActualTransaction =
            when {
                !updated.reconciled -> updated
                updated.accountId != original.accountId -> updated.copy(reconciled = false)
                !updated.cleared -> updated.copy(cleared = true)
                else -> updated
            }

        internal fun enforceOffBudgetCategoryPolicy(
            form: ActualTransactionForm,
            offBudgetAccountIds: Set<String>,
        ): ActualTransactionForm = if (
            form.type != ActualTransactionType.TRANSFER && form.accountId in offBudgetAccountIds
        ) {
            form.copy(
                categoryId = null,
                splits = form.splits.map { it.copy(categoryId = null) },
            )
        } else form

        fun cents(text: String): Long? = runCatching {
            BigDecimal(text.trim()).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }
}
