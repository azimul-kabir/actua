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
)
