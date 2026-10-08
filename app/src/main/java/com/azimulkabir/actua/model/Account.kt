package com.azimulkabir.actua.model

data class Account(
    val name: String,
    val balance: Int,
    val type: String,
    val offBudget: Boolean = false,
    val closed: Boolean = false,
    val balanceCents: Long = balance.toLong() * 100,
    val id: String = name,
    val clearedCents: Long = 0,
    val unclearedCents: Long = 0,
    val reconciledCents: Long = 0,
    val note: String = "",
    val bankSyncSource: String? = null,
    val bankSyncStatus: String? = null,
    val bankSyncLastSync: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupSortOrder: Double = 0.0,
    /** Actual's `last_reconciled` (epoch milliseconds as a string), or null when never reconciled. */
    val lastReconciled: String? = null,
    /** The bank's last synced balance (Actual's `balance_current`), in cents, if any. */
    val lastSyncedBalanceCents: Long? = null,
)
