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
}

/** Pure planning plus persistence routing ported from BudgetStore.saveTransaction. */
class ActualTransactionFormService(
    private val database: ActualBudgetDatabase,
    private val writer: ActualTransactionWriter,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun plan(form: ActualTransactionForm): ActualTransactionFormPlan {
        val unsigned = cents(form.amount) ?: throw ActualTransactionFormException.InvalidAmount
        if (unsigned <= 0) throw ActualTransactionFormException.InvalidAmount
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
    fun save(form: ActualTransactionForm, original: ActualTransaction? = null): String? {
        require(form.accountId.isNotBlank())
        val notes = form.notes.takeIf(String::isNotEmpty)
        return when (val plan = plan(form)) {
            is ActualTransactionFormPlan.Transfer -> {
                if (original == null) createTransfer(form, plan, notes)
                else if (original.transferId == null) convertToTransfer(original, form, plan, notes)
                else updateTransfer(original, form, plan, notes)
                null
            }
            is ActualTransactionFormPlan.Split -> {
                if (original == null) createSplit(form, plan, notes)
                else if (original.isParent) updateSplit(original, form, plan, notes)
                else convertToSplit(original, form, plan, notes)
                null
            }
            is ActualTransactionFormPlan.Standard -> {
                if (original?.isParent == true && form.collapseSplit) {
                    collapseSplit(original, form, plan.amountCents, notes)
                    null
                } else if (original != null) {
                    val payee = resolvePayee(form.payeeName, original)
                    writer.mutate(updates = listOf(original to original.copy(
                        accountId = form.accountId,
                        date = form.date,
                        amountCents = if (original.isParent) original.amountCents else plan.amountCents,
                        payeeId = payee?.id,
                        categoryId = if (original.isParent) null else form.categoryId,
                        notes = notes,
                        cleared = form.cleared,
                    )))
                    null
                } else {
                    val payee = resolvePayee(form.payeeName, null)
                    val id = idFactory()
                    writer.createTransaction(baseTransaction(
                        id, form.accountId, form.date, plan.amountCents, payee?.id,
                        form.categoryId, notes, form.cleared, importedPayee = payee?.name,
                    ), applyRules = true)
                    id
                }
            }
        }
    }

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
        writer.createTransfer(
            baseTransaction(sourceId, form.accountId, form.date, -plan.amountCents, toPayee.id, null, notes, form.cleared, transferId = targetId),
            baseTransaction(targetId, plan.toAccountId, form.date, plan.amountCents, fromPayee.id, null, notes, form.cleared, transferId = sourceId),
        )
    }

    private fun updateTransfer(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Transfer, notes: String?) {
        if (form.accountId == plan.toAccountId) throw ActualTransactionFormException.TransferAccountsMatch
        val partner = original.transferId?.let(database::fetchTransaction)
            ?: throw ActualTransactionFormException.TransferPartnerMissing
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
            accountId = form.accountId, date = form.date, amountCents = -plan.amountCents,
            payeeId = toPayee.id, categoryId = category(sourceOriginal, form.accountId, plan.toAccountId),
            notes = notes, cleared = form.cleared,
        )
        val target = targetOriginal.copy(
            accountId = plan.toAccountId, date = form.date, amountCents = plan.amountCents,
            payeeId = fromPayee.id, categoryId = category(targetOriginal, plan.toAccountId, form.accountId),
            notes = notes, cleared = form.cleared,
        )
        writer.mutate(updates = listOf(sourceOriginal to source, targetOriginal to target))
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
        writer.mutate(updates = listOf(original to leg), inserts = listOf(partner))
    }

    private fun createSplit(form: ActualTransactionForm, plan: ActualTransactionFormPlan.Split, notes: String?) {
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
        writer.mutate(updates, inserts, existing.map(ActualTransaction::id).filterNot(retained::contains))
    }

    private fun convertToSplit(original: ActualTransaction, form: ActualTransactionForm, plan: ActualTransactionFormPlan.Split, notes: String?) {
        if (original.transferId != null || original.parentId != null) throw ActualTransactionFormException.CannotConvertToSplit
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
        writer.mutate(updates = listOf(original to parent), inserts = children)
    }

    private fun collapseSplit(original: ActualTransaction, form: ActualTransactionForm, amount: Long, notes: String?) {
        val payee = resolvePayee(form.payeeName, original)
        val updated = original.copy(
            accountId = form.accountId, date = form.date, amountCents = amount,
            payeeId = payee?.id, categoryId = form.categoryId, notes = notes,
            cleared = form.cleared, isParent = false,
        )
        writer.mutate(
            updates = listOf(original to updated),
            tombstoneIds = database.fetchChildTransactions(original.id).map(ActualTransaction::id),
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
        fun cents(text: String): Long? = runCatching {
            BigDecimal(text.trim()).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }
}
