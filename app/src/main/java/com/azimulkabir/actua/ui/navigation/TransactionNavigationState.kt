package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.azimulkabir.actua.model.Transaction
import androidx.compose.runtime.mutableIntStateOf

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class TransactionNavigationState(
    val transactionAccount: MutableState<String?>,
    val lastOpenAccountId: MutableState<String?>,
    val transactionCategory: MutableState<String?>,
    val transactionMonth: MutableState<String?>,
    val transactionSearch: MutableState<String>,
    val searchReturnsToReports: MutableState<Boolean>,
    val editingTransaction: MutableState<Transaction?>,
    val newTransactionType: MutableState<com.azimulkabir.actua.model.Type>,
    val transactionPrefill: MutableState<com.azimulkabir.actua.ui.transactions.TransactionPrefill?>,
    val editorReturnsToTransactions: MutableState<Boolean>,
    val editorReturnsToCategory: MutableState<Boolean>,
    val editorReturnCategory: MutableState<String?>,
    val editorReturnsToReports: MutableState<Boolean>,
    val editorReturnsToReportsDetail: MutableState<DetailDestination?>,
    val addOrigin: MutableState<MainDestination>,
    val transactionsRefreshing: MutableState<Boolean>,
    val accountsRefreshing: MutableState<Boolean>,
    val reconcileOpen: MutableState<Boolean>,
    val keptReceiptPhoto: MutableState<java.io.File?>,
    val receiptPhotoVersion: MutableState<Int>,
    val editorReturnsToStatementDetail: MutableState<Boolean>,
)

@Composable
internal fun rememberTransactionNavigationState(): TransactionNavigationState =
    TransactionNavigationState(
        transactionAccount = rememberSaveable { mutableStateOf<String?>(null) },
        lastOpenAccountId = rememberSaveable { mutableStateOf<String?>(null) },
        transactionCategory = rememberSaveable { mutableStateOf<String?>(null) },
        transactionMonth = rememberSaveable { mutableStateOf<String?>(null) },
        transactionSearch = rememberSaveable { mutableStateOf("") },
        searchReturnsToReports = rememberSaveable { mutableStateOf(false) },
        editingTransaction = remember { mutableStateOf<Transaction?>(null) },
        newTransactionType = remember { mutableStateOf(com.azimulkabir.actua.model.Type.EXPENSE) },
        transactionPrefill = remember { mutableStateOf<com.azimulkabir.actua.ui.transactions.TransactionPrefill?>(null) },
        editorReturnsToTransactions = rememberSaveable { mutableStateOf(false) },
        editorReturnsToCategory = rememberSaveable { mutableStateOf(false) },
        editorReturnCategory = rememberSaveable { mutableStateOf<String?>(null) },
        editorReturnsToReports = rememberSaveable { mutableStateOf(false) },
        editorReturnsToReportsDetail = rememberSaveable { mutableStateOf<DetailDestination?>(null) },
        addOrigin = rememberSaveable { mutableStateOf(MainDestination.Accounts) },
        transactionsRefreshing = remember { mutableStateOf(false) },
        accountsRefreshing = remember { mutableStateOf(false) },
        reconcileOpen = remember { mutableStateOf(false) },
        keptReceiptPhoto = remember { mutableStateOf<java.io.File?>(null) },
        receiptPhotoVersion = remember { mutableIntStateOf(0) },
        editorReturnsToStatementDetail = rememberSaveable { mutableStateOf(false) },
    )
