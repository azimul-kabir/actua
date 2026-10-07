package com.azimulkabir.actua.data.budget.model

enum class ActualAccountType {
    CHECKING,
    SAVINGS,
    CREDIT,
    INVESTMENT,
    MORTGAGE,
    DEBT,
    OTHER;

    companion object {
        fun fromDatabase(value: String?): ActualAccountType = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: CHECKING
    }
}

data class ActualAccount(
    val id: String,
    val name: String,
    val type: ActualAccountType,
    val offBudget: Boolean,
    val closed: Boolean,
    val sortOrder: Double,
    val balanceCents: Long,
    val clearedCents: Long = 0,
    val unclearedCents: Long = 0,
    val reconciledCents: Long = 0,
    val groupId: String? = null,
)

/** Actual's experimental `account_groups`: a user-named container accounts can be assigned to. */
data class ActualAccountGroup(
    val id: String,
    val name: String,
    val sortOrder: Double,
)

data class ActualPayee(
    val id: String,
    val name: String,
    val transferAccountId: String?,
)

/** A live payee as Actual's Payees page lists it (`db.getPayees`): transfer payees show their account's name. */
data class ActualManagedPayee(
    val id: String,
    val name: String,
    val transferAccountId: String?,
    val favorite: Boolean,
    val learnCategories: Boolean,
)

data class ActualCategory(
    val id: String,
    val name: String,
    val groupId: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val cleanupDef: String? = null,
)

/** Actual's `cleanup_groups` table: named pools referenced by category `cleanup_def` rows. */
data class ActualCleanupGroup(
    val id: String,
    val name: String,
    val tombstone: Boolean = false,
)

data class ActualCategoryGroup(
    val id: String,
    val name: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val sortOrder: Double,
    val categories: List<ActualCategory>,
)

data class ActualTransaction(
    val id: String,
    val accountId: String,
    val date: Int,
    val amountCents: Long,
    val payeeId: String?,
    val payeeName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val notes: String?,
    val cleared: Boolean,
    val reconciled: Boolean,
    val transferId: String?,
    val isParent: Boolean,
    val parentId: String?,
    val tombstone: Boolean,
    val sortOrder: Double?,
    val importedPayee: String?,
    val scheduleId: String?,
    val transferAccountId: String?,
    val startingBalance: Boolean = false,
    val splitPortions: List<SplitPortion> = emptyList(),
    val categoryIsIncome: Boolean? = null,
    val financialId: String? = null,
    val pending: Boolean = false,
    val rawSyncedData: String? = null,
) {
    data class SplitPortion(
        val id: String,
        val categoryName: String?,
        val amountCents: Long,
        val notes: String?,
        val payeeName: String?,
        val categoryIsIncome: Boolean? = null,
        val categoryId: String? = null,
    )
}
