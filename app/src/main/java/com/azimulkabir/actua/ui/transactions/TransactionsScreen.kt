package com.azimulkabir.actua.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.azimulkabir.actua.model.TransactionStatusFilter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.LoanStatus
import java.math.BigDecimal
import com.azimulkabir.actua.ui.settings.payoffMonthText
import com.azimulkabir.actua.ui.settings.paidOffPercentText
import com.azimulkabir.actua.data.loans.schedule
import androidx.compose.material3.LinearProgressIndicator
import com.azimulkabir.actua.ui.components.ActuaSwitchMenuItem
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.formatStoredDate
import com.azimulkabir.actua.ui.components.CalculatorAmountState
import com.azimulkabir.actua.ui.components.CompactCalculatorPad
import com.azimulkabir.actua.ui.components.coloredTagText
import com.azimulkabir.actua.ui.components.rememberActualTagColors
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaHeroSize
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaNoteEditorSheet
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.data.budget.ActiveTagRepository
import com.azimulkabir.actua.ui.theme.AmountTypography
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.success
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R

private val sampleTransactions = listOf(
    Transaction("1", "Today", "Agora Super Shop", "Groceries", "Everyday account", -2_450, true),
    Transaction("2", "Today", "Salary", "Income", "Everyday account", 72_000, true),
    Transaction("3", "Today", "Pathao", "Transport", "Credit card", -380, false),
    Transaction("4", "Yesterday", "DESCO", "Electricity", "Everyday account", -2_700, true),
    Transaction("5", "Yesterday", "Coffee World", "Dining", "Credit card", -620, false),
    Transaction("6", "1 Sep 2026", "Landlord", "Rent", "Everyday account", -35_000, true),
    Transaction("7", "1 Sep 2026", "ISP", "Internet", "Everyday account", -1_500, true),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    accountName: String?,
    /** Transactions bank sync just added to this account, marked until the account is left (#1066). */
    newTransactionIds: Set<String> = emptySet(),
    categoryName: String? = null,
    month: String? = null,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    transactions: List<Transaction> = sampleTransactions,
    transactionStatusFilter: TransactionStatusFilter = TransactionStatusFilter.ALL,
    onTransactionStatusFilterChange: (TransactionStatusFilter) -> Unit = {},
    hideDecimalPlaces: Boolean = false,
    conventionalAmountEntry: Boolean = true,
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    hideReconciledTransactions: Boolean = false,
    onHideReconciledTransactionsChange: (Boolean) -> Unit = {},
    onSetCleared: (Transaction, Boolean) -> Unit = { _, _ -> },
    onUnlockTransaction: (Transaction) -> Unit = {},
    onReconcileAccount: (Account, onReconciled: () -> Unit) -> Unit = { _, _ -> },
    onCreateReconciliationAdjustment: (Account, Long, onCreated: () -> Unit) -> Unit = { _, _, _ -> },
    onDelete: (Transaction) -> Unit = {},
    onDuplicate: (Transaction) -> Unit = {},
    onDuplicateMultiple: (List<Transaction>) -> Unit = {},
    account: Account? = null,
    creditCard: CreditCardStatus? = null,
    loan: LoanStatus? = null,
    onSaveAccountNote: (String) -> Unit = {},
    searchTransactions: (suspend (String) -> List<Transaction>)? = null,
    initialSearch: String = "",
    showBackButton: Boolean = true,
    showCurrentBalanceSummary: Boolean = true,
    onShowCurrentBalanceSummaryChange: (Boolean) -> Unit = {},
    showRunningBalance: Boolean = false,
    onShowRunningBalanceChange: (Boolean) -> Unit = {},
    showNotes: Boolean = true,
    allTransactions: List<Transaction> = transactions,
    onReconcileVisibilityChange: (Boolean) -> Unit = {},
    returnToRootRequest: Int = 0,
    onDeleteMultiple: (List<Transaction>) -> Unit = {},
    onLinkSchedule: (List<Transaction>, String) -> Unit = { _, _ -> },
    onUnlinkSchedule: (List<Transaction>) -> Unit = {},
    onViewSchedule: (String) -> Unit = {},
    linkableSchedules: List<ScheduleOption> = emptyList(),
    onViewStatements: (() -> Unit)? = null,
    onPayStatement: ((remainingDueCents: Long) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    upcomingTransactions: List<Transaction> = emptyList(),
    showUpcomingTransactions: Boolean = true,
    onShowUpcomingTransactionsChange: (Boolean) -> Unit = {},
    recurringScheduleIds: Set<String> = emptySet(),
    onUpcomingAction: (Transaction, UpcomingTransactionAction) -> Unit = { _, _ -> },
    hasFab: Boolean = true,
    categoryOptions: List<String> = emptyList(),
    onCategorizeMultiple: (List<Transaction>, String) -> Unit = { _, _ -> },
    accountOptions: List<String> = emptyList(),
    onMoveMultiple: (List<Transaction>, String) -> Unit = { _, _ -> },
    onLabelMultiple: (List<Transaction>, String) -> Unit = { _, _ -> },
    onMergeTransactions: (Transaction, Transaction) -> Unit = { _, _ -> },
    onMakeTransfer: (Transaction, Transaction) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    var search by remember(initialSearch) { mutableStateOf(initialSearch) }
    var showSearch by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var viewed by remember { mutableStateOf<Transaction?>(null) }
    var upcomingMenuFor by remember { mutableStateOf<Transaction?>(null) }
    var selectionModeOn by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(selectionModeOn) { if (!selectionModeOn) selectedIds = emptySet() }
    var bulkMenuOpen by remember { mutableStateOf(false) }
    var showLinkSchedulePicker by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    // A bulk edit of reconciled rows waits here for confirmation (#746).
    var pendingReconciledEdit by remember { mutableStateOf<Pair<Int, () -> Unit>?>(null) }
    fun confirmReconciled(rows: List<Transaction>, edit: () -> Unit) {
        val warning = reconciledWarningRes(rows, ReconciledAction.BULK_EDIT)
        if (warning == null) edit() else pendingReconciledEdit = warning to edit
    }
    val toggleCleared = rememberClearedToggle(onSetCleared, onUnlockTransaction)
    var showCategorizePicker by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var tagVersion by remember { mutableStateOf(0L) }
    var reconcileOpen by remember(account?.id) { mutableStateOf(false) }
    LaunchedEffect(reconcileOpen) { onReconcileVisibilityChange(reconcileOpen) }
    DisposableEffect(Unit) {
        onDispose { onReconcileVisibilityChange(false) }
    }
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) {
            when {
                viewed != null -> viewed = null
                showSearch -> showSearch = false
                else -> listState.animateScrollToItem(0)
            }
        }
    }
    var accountNote by remember(account) { mutableStateOf(account?.note.orEmpty()) }
    val context = LocalContext.current
    val tagColors = rememberActualTagColors(transactions)
    val tagRepository = remember(context) { ActiveTagRepository(context) }
    val availableTags = remember(tagVersion) { tagRepository.tags(tagVersion) }
    val accountDetailPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "account_detail_preferences",
            android.content.Context.MODE_PRIVATE,
        )
    }
    var showAccountNotes by remember(account?.id) {
        mutableStateOf(account?.let {
            accountDetailPreferences.getBoolean("show_notes_${it.id}", true)
        } ?: true)
    }
    var showCreditCardSection by remember(account?.id) {
        mutableStateOf(account?.let {
            accountDetailPreferences.getBoolean("show_credit_card_section_${it.id}", true)
        } ?: true)
    }

    var searchResults by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var completedQuery by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf(false) }
    LaunchedEffect(search, transactions, searchTransactions) {
        completedQuery = null
        searchError = false
        if (search.isNotBlank() && searchTransactions != null) {
            delay(200)
            try {
                searchResults = searchTransactions(search)
                completedQuery = search
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                searchError = true
            }
        }
    }
    val searchingDatabase = search.isNotBlank() && searchTransactions != null
    val candidates = if (searchingDatabase) {
        if (completedQuery == search) searchResults else emptyList()
    } else transactions
    // Upcoming rows are a display-only projection: they're merged in here (and re-sorted, since
    // `transactions` is already newest-first) rather than upstream, so search results, the
    // reconciliation register, and running-balance folds never see synthetic rows.
    val candidatesWithUpcoming = remember(candidates, showUpcomingTransactions, upcomingTransactions, searchingDatabase, transactionStatusFilter) {
        if (!searchingDatabase && showUpcomingTransactions && transactionStatusFilter == TransactionStatusFilter.ALL && upcomingTransactions.isNotEmpty()) {
            (candidates + upcomingTransactions).sortedByDescending { it.date }
        } else candidates
    }
    // `candidatesWithUpcoming`/filters change far less often than this composable recomposes
    // (e.g. every row tap in selection mode), so this needs its own remember rather than
    // recomputing the filter over the whole transaction list on every recomposition.
    val visible = remember(candidatesWithUpcoming, accountName, categoryName, month, hideReconciledTransactions, transactionStatusFilter, searchingDatabase, search) {
        candidatesWithUpcoming.filter {
            (accountName == null || it.account == accountName) &&
                (categoryName == null || it.category == categoryName) &&
                (month == null || it.date.filter(Char::isDigit).startsWith(month.replace("-", ""))) &&
                !hiddenAsReconciled(it, transactionStatusFilter, hideReconciledTransactions) &&
                (searchingDatabase || search.isBlank() ||
                    (listOf(it.payee, it.category, it.account, it.notes, it.transferAccount.orEmpty()) +
                        it.splits.flatMap { split -> listOf(split.payee, split.notes, split.category) })
                        .any { text -> text.contains(search, ignoreCase = true) })
        }
    }
    // Otherwise re-grouped inside the LazyColumn content block on every recomposition of this
    // screen (e.g. every selection-mode tap), not just when `visible` actually changes.
    val groupedByDate = remember(visible) { visible.groupBy { it.date } }
    val selectedTransactions = remember(visible, selectedIds) { visible.filter { it.id in selectedIds } }
    // Selection order, not list order: on a tie Actual keeps the second transaction picked.
    val mergePair = remember(visible, selectedIds) {
        val byId = visible.associateBy { it.id }
        mergeablePair(selectedIds.mapNotNull(byId::get))
    }
    val transferPair = remember(visible, selectedIds) {
        val byId = visible.associateBy { it.id }
        transferablePair(selectedIds.mapNotNull(byId::get))
    }
    val runningBalances = remember(allTransactions, accountName, showRunningBalance) {
        if (showRunningBalance && accountName != null) accountRunningBalances(allTransactions, accountName)
        else emptyMap()
    }

    AnimatedContent(
        targetState = reconcileOpen && account != null,
        transitionSpec = {
            if (targetState) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
            // Reconciliation and the register are both large trees. Only their layers should
            // move during navigation; interpolating bounds causes avoidable list remeasurement.
            }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
        },
        label = "Reconcile navigation motion",
    ) { showingReconcile ->
    if (showingReconcile && account != null) {
        ReconcileAccountScreen(
            modifier = modifier,
            account = account,
            // Otherwise re-filtered on every recomposition of this AnimatedContent branch, not
            // just when `transactions`/`account` actually change.
            transactions = remember(transactions, account.name) { transactions.filter { it.account == account.name } },
            // Reconciliation always shows exact cents, even when normal lists hide decimals.
            hideDecimalPlaces = false,
            conventionalAmountEntry = conventionalAmountEntry,
            onBack = { reconcileOpen = false },
            onSetCleared = onSetCleared,
            onReconcile = { onReconcileAccount(account) { reconcileOpen = false } },
            onCreateAdjustment = { difference, onCreated -> onCreateReconciliationAdjustment(account, difference, onCreated) },
        )
    } else Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(
            title = categoryName ?: accountName
                ?: if (showBackButton) stringResource(R.string.transactions_all_accounts)
                else stringResource(R.string.transactions_title),
            onBack = if (showBackButton) onBack else null,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.transactions_search))
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.transactions_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ActuaSwitchMenuItem(stringResource(R.string.transactions_group_by_date), groupTransactionsByDate, onGroupTransactionsByDateChange)
                            ActuaSwitchMenuItem(stringResource(R.string.transactions_show_upcoming), showUpcomingTransactions, onShowUpcomingTransactionsChange)
                            account?.let { selectedAccount ->
                                if (!selectedAccount.closed) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.transactions_reconcile)) },
                                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                        onClick = { menuOpen = false; reconcileOpen = true },
                                    )
                                    HorizontalDivider()
                                }
                                ActuaSwitchMenuItem(
                                    stringResource(R.string.transactions_hide_reconciled),
                                    hideReconciledTransactions,
                                    onHideReconciledTransactionsChange,
                                )
                                ActuaSwitchMenuItem(
                                    stringResource(R.string.transactions_show_balance_summary),
                                    showCurrentBalanceSummary,
                                    onShowCurrentBalanceSummaryChange,
                                )
                                ActuaSwitchMenuItem(
                                    stringResource(R.string.transactions_running_balance),
                                    showRunningBalance,
                                    onShowRunningBalanceChange,
                                )
                                if (showNotes) {
                                    ActuaSwitchMenuItem(stringResource(R.string.transactions_show_notes), showAccountNotes, onCheckedChange = { show ->
                                        showAccountNotes = show
                                        accountDetailPreferences.edit()
                                            .putBoolean("show_notes_${selectedAccount.id}", show).apply()
                                    })
                                }
                                if (creditCard != null) {
                                    ActuaSwitchMenuItem(stringResource(R.string.transactions_show_credit_card), showCreditCardSection, onCheckedChange = { show ->
                                        showCreditCardSection = show
                                        accountDetailPreferences.edit()
                                            .putBoolean("show_credit_card_section_${selectedAccount.id}", show).apply()
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text(stringResource(R.string.transactions_search)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = selectionModeOn,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectedIds.isEmpty()) stringResource(R.string.transactions_tap_to_select)
                        else pluralStringResource(R.plurals.transactions_selected, selectedIds.size, selectedIds.size),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { selectionModeOn = false }) { Text(stringResource(R.string.action_cancel)) }
                    IconButton(onClick = { showCategorizePicker = true }, enabled = selectedTransactions.isNotEmpty()) {
                        Icon(Icons.Outlined.Category, contentDescription = stringResource(R.string.transactions_categorize))
                    }
                    IconButton(onClick = { newTagName = ""; showLabelPicker = true }, enabled = selectedTransactions.isNotEmpty()) {
                        Icon(Icons.Outlined.Sell, contentDescription = stringResource(R.string.transactions_label))
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { bulkMenuOpen = true }, enabled = selectedTransactions.isNotEmpty()) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.transactions_more_actions))
                        }
                        DropdownMenu(expanded = bulkMenuOpen, onDismissRequest = { bulkMenuOpen = false }) {
                            if (selectedTransactions.size == 1) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = {
                                    bulkMenuOpen = false
                                    selectionModeOn = false
                                    onEdit(selectedTransactions.first())
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_mark_cleared)) }, onClick = {
                                bulkMenuOpen = false
                                bulkClearedTargets(selectedTransactions, cleared = true).forEach { onSetCleared(it, true) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_mark_uncleared)) }, onClick = {
                                bulkMenuOpen = false
                                bulkClearedTargets(selectedTransactions, cleared = false).forEach { onSetCleared(it, false) }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = {
                                bulkMenuOpen = false
                                onDuplicateMultiple(selectedTransactions)
                                selectionModeOn = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = {
                                bulkMenuOpen = false
                                showMovePicker = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.transactions_link_schedule)) }, onClick = {
                                bulkMenuOpen = false
                                showLinkSchedulePicker = true
                            })
                            if (selectedTransactions.any { it.scheduleId != null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.transactions_unlink_schedule)) }, onClick = {
                                    bulkMenuOpen = false
                                    onUnlinkSchedule(selectedTransactions.filter { it.scheduleId != null })
                                    selectionModeOn = false
                                })
                            }
                            if (selectedTransactions.size == 1) {
                                selectedTransactions.first().scheduleId?.let { scheduleId ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.transactions_view_schedule)) }, onClick = {
                                        bulkMenuOpen = false
                                        selectionModeOn = false
                                        onViewSchedule(scheduleId)
                                    })
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transactions_merge)) },
                                enabled = mergePair != null,
                                onClick = {
                                    bulkMenuOpen = false
                                    mergePair?.let { (first, second) ->
                                        confirmReconciled(listOf(first, second)) {
                                            onMergeTransactions(first, second)
                                            selectionModeOn = false
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transactions_make_transfer)) },
                                enabled = transferPair != null,
                                onClick = {
                                    bulkMenuOpen = false
                                    transferPair?.let { (first, second) ->
                                        confirmReconciled(listOf(first, second)) {
                                            onMakeTransfer(first, second)
                                            selectionModeOn = false
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { bulkMenuOpen = false; confirmBulkDelete = true },
                            )
                        }
                    }
                }
                if (selectedTransactions.size > 1) {
                    val totalCents = selectedTransactions.sumOf { it.amountCents }
                    Text(
                        stringResource(
                            R.string.transactions_selection_total,
                            formatMoneyCents(totalCents, hideDecimalPlaces, showPositiveSign = true),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                }
            }
        }
        if (searchingDatabase && completedQuery != search) {
            Text(if (searchError) stringResource(R.string.search_failed) else stringResource(R.string.search_searching),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.ALL,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.ALL) },
                label = { Text(stringResource(R.string.transactions_filter_all)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.UNCATEGORIZED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.UNCATEGORIZED) },
                label = { Text(stringResource(R.string.transactions_filter_uncategorized)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.UNCLEARED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.UNCLEARED) },
                label = { Text(stringResource(R.string.transactions_filter_uncleared)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.CLEARED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.CLEARED) },
                label = { Text(stringResource(R.string.transactions_filter_cleared)) }
            )
            FilterChip(
                selected = transactionStatusFilter == TransactionStatusFilter.RECONCILED,
                onClick = { onTransactionStatusFilterChange(TransactionStatusFilter.RECONCILED) },
                label = { Text(stringResource(R.string.transactions_filter_reconciled)) }
            )
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (hasFab) 96.dp else 0.dp),
            ) {
                account?.let { selectedAccount ->
                    item("account-details") {
                        AccountDetails(
                            selectedAccount,
                            creditCard,
                            accountNote,
                            { savedNote -> accountNote = savedNote; onSaveAccountNote(savedNote) },
                            hideDecimalPlaces,
                            showCurrentBalanceSummary,
                            showNotes && showAccountNotes,
                            onViewStatements,
                            showCreditCardSection,
                            onPayStatement,
                            loan,
                        )
                    }
                }
                item("transaction-total") {
                    val posted = visible.filterNot { it.isUpcoming }
                    val total = posted.sumOf { it.amountCents }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 10.dp)) {
                        Text(
                            pluralStringResource(R.plurals.transactions_count, posted.size, posted.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Amount(total, FontWeight.Bold, hideDecimalPlaces)
                    }
                }
                if (visible.isEmpty()) item("empty-transactions") {
                    Text(
                        when {
                            search.isNotBlank() -> stringResource(R.string.search_no_matching_transactions)
                            transactionStatusFilter == TransactionStatusFilter.UNCATEGORIZED -> stringResource(R.string.transactions_empty_uncategorized)
                            transactionStatusFilter == TransactionStatusFilter.UNCLEARED -> stringResource(R.string.transactions_empty_uncleared)
                            transactionStatusFilter == TransactionStatusFilter.CLEARED -> stringResource(R.string.transactions_empty_cleared)
                            transactionStatusFilter == TransactionStatusFilter.RECONCILED -> stringResource(R.string.transactions_empty_reconciled)
                            hideReconciledTransactions -> stringResource(R.string.transactions_empty_unreconciled)
                            else -> stringResource(R.string.transactions_empty)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screenHorizontal),
                    )
                }
                if (groupTransactionsByDate) {
                    groupedByDate.forEach { (date, transactions) ->
                        stickyHeader(key = date) {
                            // Sticky, so it keeps the opaque page background under the day label.
                            Text(formatTransactionDate(date), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                                    .padding(start = Spacing.screenHorizontal + Spacing.xs, end = Spacing.screenHorizontal,
                                        top = Spacing.md, bottom = Spacing.xs))
                        }
                        itemsIndexed(transactions, key = { _, transaction -> transaction.id }) { index, transaction ->
                            // Every visible row reads the same `selectedIds` set, so a plain
                            // `transaction.id in selectedIds` recomposes every visible row on any
                            // selection toggle. derivedStateOf only reports a change (and thus only
                            // recomposes) the row(s) whose membership actually flipped.
                            val isSelected by remember(transaction.id) { derivedStateOf { transaction.id in selectedIds } }
                            ActuaGroupedItem(GroupPosition.of(index, transactions.size), dividerInset = TRANSACTION_DIVIDER_INSET) {
                                TransactionRow(transaction, hideDecimalPlaces, showDate = false,
                                    onClick = {
                                        if (transaction.isUpcoming) {
                                            if (!selectionModeOn) upcomingMenuFor = transaction
                                        } else if (selectionModeOn) {
                                            selectedIds = selectedIds.toggle(transaction.id)
                                        } else viewed = transaction
                                    },
                                    showAccount = accountName == null,
                                    onLongClick = {
                                        if (!transaction.isUpcoming) {
                                            if (selectionModeOn) selectedIds = selectedIds.toggle(transaction.id)
                                            else {
                                                selectionModeOn = true
                                                selectedIds = setOf(transaction.id)
                                            }
                                        }
                                    },
                                    onClearedClick = { toggleCleared(transaction) }, tagColors = tagColors,
                                    selectionMode = selectionModeOn, selected = isSelected,
                                    isNew = transaction.id in newTransactionIds, runningBalanceCents = runningBalances[transaction.id], showDivider = false)
                            }
                        }
                    }
                } else {
                    itemsIndexed(visible, key = { _, transaction -> transaction.id }) { index, transaction ->
                        val isSelected by remember(transaction.id) { derivedStateOf { transaction.id in selectedIds } }
                        ActuaGroupedItem(GroupPosition.of(index, visible.size), dividerInset = TRANSACTION_DIVIDER_INSET) {
                            TransactionRow(transaction, hideDecimalPlaces, showDate = true,
                                onClick = {
                                    if (transaction.isUpcoming) {
                                        if (!selectionModeOn) upcomingMenuFor = transaction
                                    } else if (selectionModeOn) {
                                        selectedIds = selectedIds.toggle(transaction.id)
                                    } else viewed = transaction
                                },
                                showAccount = accountName == null,
                                onLongClick = {
                                    if (!transaction.isUpcoming) {
                                        if (selectionModeOn) selectedIds = selectedIds.toggle(transaction.id)
                                        else {
                                            selectionModeOn = true
                                            selectedIds = setOf(transaction.id)
                                        }
                                    }
                                },
                                onClearedClick = { toggleCleared(transaction) }, tagColors = tagColors,
                                selectionMode = selectionModeOn, selected = isSelected,
                                isNew = transaction.id in newTransactionIds, runningBalanceCents = runningBalances[transaction.id], showDivider = false)
                        }
                    }
                }
            }
        }
    }
    }
    viewed?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { viewed = null },
            onEdit = { viewed = null; onEdit(transaction) },
            onDelete = { viewed = null; onDelete(transaction) },
            onDuplicate = { viewed = null; onDuplicate(transaction) },
            tagColors = tagColors,
        )
    }
    upcomingMenuFor?.let { transaction ->
        ModalBottomSheet(onDismissRequest = { upcomingMenuFor = null }) {
            ActuaSheetContent {
                val scheduleId = transaction.scheduleId
                ActuaSheetTitle(transaction.payee)
                ActuaSheetCard {
                    ActuaFormRow(icon = Icons.Outlined.CalendarMonth, label = stringResource(R.string.transactions_scheduled_date),
                        value = formatTransactionDate(transaction.date))
                }
                ActuaSheetCard {
                    upcomingTransactionActions(recurring = scheduleId in recurringScheduleIds).forEachIndexed { index, action ->
                        if (index > 0) ActuaCardDivider()
                        ActuaSheetAction(stringResource(action.labelRes), icon = upcomingActionIcon(action), onClick = {
                            upcomingMenuFor = null
                            onUpcomingAction(transaction, action)
                        })
                    }
                    if (scheduleId != null) {
                        ActuaCardDivider()
                        ActuaSheetAction(stringResource(R.string.transactions_edit_schedule), icon = Icons.Outlined.Edit, onClick = {
                            upcomingMenuFor = null
                            onViewSchedule(scheduleId)
                        })
                    }
                }
            }
        }
    }
    if (confirmBulkDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = {
                Text(pluralStringResource(R.plurals.transactions_delete_count_question, count, count))
            },
            text = {
                Text(
                    stringResource(
                        reconciledWarningRes(visible.filter { it.id in selectedIds }, ReconciledAction.DELETE)
                            ?: R.string.transactions_delete_multiple_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMultiple(visible.filter { it.id in selectedIds })
                    confirmBulkDelete = false
                    selectionModeOn = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    pendingReconciledEdit?.let { (warning, edit) ->
        ReconciledConfirmDialog(
            messageRes = warning,
            onConfirm = {
                pendingReconciledEdit = null
                edit()
            },
            onDismiss = { pendingReconciledEdit = null },
        )
    }
    if (showLinkSchedulePicker) {
        ModalBottomSheet(onDismissRequest = { showLinkSchedulePicker = false }) {
            ActuaSheetContent {
                ActuaSheetTitle(stringResource(R.string.transactions_link_schedule))
                if (linkableSchedules.isEmpty()) {
                    Text(stringResource(R.string.transactions_no_schedules), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs))
                } else {
                    val ids = selectedIds
                    ActuaSheetCard {
                        linkableSchedules.forEachIndexed { index, schedule ->
                            if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                            Action(schedule.name) {
                                showLinkSchedulePicker = false
                                onLinkSchedule(visible.filter { it.id in ids }, schedule.id)
                                selectionModeOn = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCategorizePicker) {
        ModalBottomSheet(onDismissRequest = { showCategorizePicker = false }) {
            ActuaSheetContent {
                ActuaSheetTitle(stringResource(R.string.transactions_categorize))
                if (categoryOptions.isEmpty()) {
                    Text(stringResource(R.string.transactions_no_categories), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs))
                } else {
                    val toCategorize = selectedTransactions
                    ActuaSheetCard {
                        categoryOptions.forEachIndexed { index, category ->
                            if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                            Action(category) {
                                showCategorizePicker = false
                                confirmReconciled(toCategorize) {
                                    onCategorizeMultiple(toCategorize, category)
                                    selectionModeOn = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showMovePicker) {
        ModalBottomSheet(onDismissRequest = { showMovePicker = false }) {
            ActuaSheetContent {
                ActuaSheetTitle(stringResource(R.string.transactions_move_to_account))
                if (accountOptions.isEmpty()) {
                    Text(stringResource(R.string.transactions_no_accounts), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs))
                } else {
                    val toMove = selectedTransactions
                    ActuaSheetCard {
                        accountOptions.forEachIndexed { index, account ->
                            if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                            Action(account) {
                                showMovePicker = false
                                confirmReconciled(toMove) {
                                    onMoveMultiple(toMove, account)
                                    selectionModeOn = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showLabelPicker) {
        ModalBottomSheet(onDismissRequest = { showLabelPicker = false }) {
            ActuaSheetContent {
                ActuaSheetTitle(stringResource(R.string.transactions_label))
                val toLabel = selectedTransactions
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        placeholder = { Text(stringResource(R.string.transactions_new_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newTagName.isNotBlank(),
                        onClick = {
                            val created = runCatching { tagRepository.create(newTagName) }.getOrNull()
                            if (created != null) {
                                tagVersion += 1
                                showLabelPicker = false
                                onLabelMultiple(toLabel, created.tag)
                                selectionModeOn = false
                            }
                        },
                    ) { Text(stringResource(R.string.action_add)) }
                }
                if (availableTags.isEmpty()) {
                    Text(stringResource(R.string.transactions_no_labels), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs))
                } else {
                    ActuaSheetCard {
                        availableTags.forEachIndexed { index, tag ->
                            if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                            Action(tag.tag) {
                                showLabelPicker = false
                                onLabelMultiple(toLabel, tag.tag)
                                selectionModeOn = false
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

data class ScheduleOption(val id: String, val name: String)

@Composable
private fun ReconcileAccountScreen(
    modifier: Modifier = Modifier,
    account: Account,
    transactions: List<Transaction>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onSetCleared: (Transaction, Boolean) -> Unit,
    onReconcile: () -> Unit,
    onCreateAdjustment: (Long, onCreated: () -> Unit) -> Unit,
) {
    var bankBalance by remember(account.id) { mutableStateOf<Long?>(null) }
    var calculatorKey by remember(account.id) { mutableStateOf(0) }
    val calculator = remember(account.id, calculatorKey) {
        CalculatorAmountState(bankBalance ?: 0L, allowsNegative = true,
            conventionalAmountEntry = conventionalAmountEntry)
    }
    var reviewExpanded by remember(account.id) { mutableStateOf(false) }
    var adjustmentConfirmation by remember { mutableStateOf<Long?>(null) }
    // Otherwise re-filtered on every recomposition of this screen, including every digit typed
    // into the bank-balance calculator and every reviewExpanded toggle.
    val uncleared = remember(transactions) { transactions.filter { !it.cleared && !it.reconciled } }
    val difference = bankBalance?.minus(account.clearedCents)
    BackHandler(onBack = onBack)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.transactions_reconcile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(account.name, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("balances") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.reconcile_cleared_balance), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatReconciliationMoney(account.clearedCents, hideDecimalPlaces),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.reconcile_bank_balance_question), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                bankBalance?.let { formatReconciliationMoney(it, hideDecimalPlaces) }
                                    ?: stringResource(R.string.reconcile_enter_bank_balance),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bankBalance == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = {
                                bankBalance = account.clearedCents
                                calculatorKey++
                            }) { Text(stringResource(R.string.reconcile_use_cleared_balance)) }
                            // Actual's "Last Balance from Bank" / "Use last synced total" (#995).
                            account.lastSyncedBalanceCents?.let { synced ->
                                Text(stringResource(
                                    R.string.reconcile_last_bank_balance,
                                    formatReconciliationMoney(synced, hideDecimalPlaces),
                                ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = {
                                    bankBalance = synced
                                    calculatorKey++
                                }) { Text(stringResource(R.string.reconcile_use_last_synced_total)) }
                            }
                        }
                    }
                }
            }

            item("last-reconciled") {
                // Actual's "Reconciled {relative} ({date})" / "Not yet reconciled" line (#994).
                val reconciledAt = lastReconciledMillis(account.lastReconciled)
                Text(
                    if (reconciledAt == null) stringResource(R.string.reconcile_not_yet) else {
                        val relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                            reconciledAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        )
                        val date = formatDate(java.time.Instant.ofEpochMilli(reconciledAt)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                        stringResource(R.string.reconcile_reconciled_at, relative, date)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            difference?.let { amount ->
                if (amount == 0L) {
                    item("match") {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Text(stringResource(R.string.transaction_reconciled), style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(stringResource(R.string.reconcile_balance_matches),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center)
                                Button(onClick = onReconcile, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.reconcile_lock_transactions))
                                }
                                Text(stringResource(R.string.reconcile_lock_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    item("difference") {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.reconcile_difference), style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text((if (amount > 0L) "+" else "") + formatReconciliationMoney(amount, hideDecimalPlaces),
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                FilledTonalButton(onClick = { reviewExpanded = !reviewExpanded },
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (uncleared.isEmpty()) stringResource(R.string.reconcile_no_uncleared_review)
                                        else pluralStringResource(R.plurals.reconcile_review_uncleared, uncleared.size, uncleared.size))
                                }
                                TextButton(onClick = { adjustmentConfirmation = amount }, modifier = Modifier.align(Alignment.End)) {
                                    Text(stringResource(R.string.reconcile_create_adjustment))
                                }
                            }
                        }
                    }
                    if (reviewExpanded) {
                        if (uncleared.isEmpty()) item("no-uncleared") {
                            Text(stringResource(R.string.reconcile_difference_explanation),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp))
                        } else items(uncleared, key = { "reconcile-${it.id}" }) { transaction ->
                            ReconciliationTransactionRow(transaction, hideDecimalPlaces) {
                                onSetCleared(transaction, true)
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
            CompactCalculatorPad(
                calculator = calculator,
                conventionalAmountEntry = conventionalAmountEntry,
                allowSign = true,
                showDisplay = false,
                onValueChange = { bankBalance = it },
                onDone = { bankBalance = calculator.finish() },
            )
        }
    }

    adjustmentConfirmation?.let { amount ->
        AlertDialog(
            onDismissRequest = { adjustmentConfirmation = null },
            title = { Text(stringResource(R.string.reconcile_create_adjustment_question)) },
            text = {
                Text(stringResource(
                    R.string.reconcile_adjustment_message,
                    formatReconciliationMoney(amount, hideDecimalPlaces),
                ))
            },
            confirmButton = { TextButton(onClick = {
                onCreateAdjustment(amount) { adjustmentConfirmation = null }
            }) { Text(stringResource(R.string.reconcile_create_adjustment_action)) } },
            dismissButton = { TextButton(onClick = { adjustmentConfirmation = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ReconciliationTransactionRow(
    transaction: Transaction,
    hideDecimalPlaces: Boolean,
    onMarkCleared: () -> Unit,
) {
    Surface(onClick = onMarkCleared, color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transaction.payee.ifBlank { stringResource(R.string.transaction_unknown_payee) }, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(formatStoredDate(transaction.date), transaction.category)
                    .filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatReconciliationMoney(transaction.amountCents, hideDecimalPlaces),
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
            Checkbox(checked = false, onCheckedChange = { onMarkCleared() })
        }
    }
}

/** Actual's `last_reconciled` (epoch milliseconds as a string), or null when absent or unreadable. */
internal fun lastReconciledMillis(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it > 0 }

private fun formatReconciliationMoney(cents: Long, hideDecimalPlaces: Boolean): String =
    formatMoneyCents(cents, hideDecimalPlaces, respectBalanceVisibility = false)

private fun com.azimulkabir.actua.data.schedules.DayDate.formatted(): String =
    java.time.LocalDate.of(year, month, day)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, yyyy", java.util.Locale.ENGLISH))

@Composable
internal fun AccountDetails(account: Account, card: CreditCardStatus?, note: String,
    onSaveNote: (String) -> Unit, hideDecimals: Boolean, showSummary: Boolean, showNotes: Boolean,
    onViewStatements: (() -> Unit)? = null, showCreditCardSection: Boolean = true,
    onPayStatement: ((Long) -> Unit)? = null, loan: LoanStatus? = null) {
    val context = LocalContext.current
    val detailPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("account_detail_preferences", android.content.Context.MODE_PRIVATE)
    }
    var balanceExpanded by remember(account.id) {
        mutableStateOf(detailPreferences.getBoolean("balance_expanded_${account.id}", false))
    }
    var noteEditorOpen by remember(account.id) { mutableStateOf(false) }
    val toggleBalance = {
        balanceExpanded = !balanceExpanded
        detailPreferences.edit().putBoolean("balance_expanded_${account.id}", balanceExpanded).apply()
    }
    val balanceArrowRotation by animateFloatAsState(
        targetValue = if (balanceExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "Balance disclosure",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSummary) {
            val balanceText = formatMoneyCents(account.balanceCents, hideDecimals)
            ActuaHeroAmount(
                amount = balanceText,
                caption = stringResource(R.string.account_balance),
                captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                amountColor = when {
                    account.balanceCents > 0 -> MaterialTheme.colorScheme.primary
                    account.balanceCents < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                size = ActuaHeroSize.Medium,
                contentDescription = stringResource(R.string.account_balance_description, balanceText),
            )
        }
        if (showSummary) ActuaFormCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BalanceColumn(stringResource(R.string.transaction_cleared), account.clearedCents, hideDecimals, Modifier.weight(1f), alignment = Alignment.Start)
                    BalanceColumn(stringResource(R.string.transaction_uncleared), account.unclearedCents, hideDecimals, Modifier.weight(1f), alignment = Alignment.End)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = toggleBalance),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.transaction_reconciled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (balanceExpanded) stringResource(R.string.account_reconciled_details_collapse)
                        else stringResource(R.string.account_reconciled_details_show),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp).rotate(balanceArrowRotation),
                    )
                }
                AnimatedVisibility(
                    visible = balanceExpanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        card?.availableCreditCents?.let { DetailAmount(stringResource(R.string.account_available_credit), it, hideDecimals) }
                        card?.config?.limitCents?.let { DetailAmount(stringResource(R.string.account_credit_limit), it, hideDecimals) }
                        if (card != null) HorizontalDivider()
                        DetailAmount(stringResource(R.string.transaction_reconciled), account.reconciledCents, hideDecimals)
                    }
                }
            }
        }
        loan?.let { LoanOverviewCard(it, hideDecimals) }
        if (showCreditCardSection) card?.let {
            ActuaFormCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_billing_cycle), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val (cycleStart, cycleEnd) = it.cycle.cycleRange()
                    val statementDue = it.pendingStatementDue()
                    val closedStatement = statementDue?.let { due ->
                        it.cycle.statementDueCycles().firstOrNull { c -> c.dueDate == due.dueDate }
                    }
                    // The pending payment belongs to the statement that already closed, not to the open cycle.
                    if (statementDue != null) {
                        Text(stringResource(R.string.account_last_statement), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        closedStatement?.let { closed ->
                            Text(stringResource(R.string.account_statement_closed, closed.end.formatted()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(it.cycle.dueSummary(dueDate = statementDue.dueDate), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                        if (statementDue.isPaid) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.account_statement_due), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.account_statement_paid), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.success)
                            }
                        } else {
                            DetailAmount(stringResource(R.string.account_statement_due), statementDue.remainingDue, hideDecimals)
                            if (onPayStatement != null && !it.closed) {
                                FilledTonalButton(onClick = { onPayStatement(statementDue.remainingDue) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.account_pay_statement))
                                }
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.account_current_cycle), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.account_cycle_range, cycleStart.formatted(), cycleEnd.formatted()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (statementDue == null) {
                        Text(it.cycle.dueSummary(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    DetailAmount(stringResource(R.string.account_cycle_spend), it.cycleSpendCents, hideDecimals)
                    it.previousCycleSpendCents?.let { previous ->
                        DetailAmount(stringResource(R.string.account_previous_cycle_spend), previous, hideDecimals)
                    }
                    it.utilizationPercent?.let { percent ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.account_credit_utilization), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.account_credit_utilization_value, percent), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (onViewStatements != null) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onViewStatements),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.account_statement_history),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
        if (showNotes) Text(stringResource(R.string.account_note), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (showNotes) Surface(
            onClick = { noteEditorOpen = true },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = note.ifBlank { stringResource(R.string.account_add_note) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = if (note.isBlank()) 1 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (noteEditorOpen) {
        ActuaNoteEditorSheet(
            initialNote = note,
            placeholder = stringResource(R.string.account_note_placeholder),
            onDismiss = { noteEditorOpen = false },
            onSave = {
                onSaveNote(it)
                noteEditorOpen = false
            },
        )
    }
}

@Composable
private fun BalanceColumn(label: String, amountCents: Long, hideDecimals: Boolean,
    modifier: Modifier = Modifier, emphasized: Boolean = false, alignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
    val formatted = formatMoneyCents(amountCents, hideDecimals)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label $formatted" },
        horizontalAlignment = alignment,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatted,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Payoff progress, balance, payment schedule and projected payoff for a tracked loan account. */
@Composable
private fun LoanOverviewCard(loan: LoanStatus, hideDecimals: Boolean) {
    val config = loan.config
    val schedule = remember(loan.balanceCents, config) {
        if (loan.isPaidOff) null else config.schedule(loan.balanceCents)
    }
    ActuaFormCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.account_loan), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val progress = if (loan.isPaidOff) stringResource(R.string.loans_paid_off)
                else stringResource(R.string.loans_paid_off_percent, paidOffPercentText(loan.paidOffPermille))
            Row(Modifier.fillMaxWidth()) {
                Text(progress, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.account_loan_of_original, formatMoneyCents(loan.paidOffCents, hideDecimals),
                    formatMoneyCents(config.originalBalanceCents, hideDecimals)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { loan.paidOffPermille / 1000f }, modifier = Modifier.fillMaxWidth())
            if (!loan.isPaidOff) DetailAmount(stringResource(R.string.account_loan_owed), loan.owedCents, hideDecimals)
            DetailAmount(stringResource(R.string.account_loan_monthly_payment), config.minimumPaymentCents, hideDecimals)
            config.escrowOrFeesCents?.let { DetailAmount(stringResource(R.string.account_loan_escrow), it, hideDecimals) }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.loans_interest_rate), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.account_loan_rate_value, BigDecimal.valueOf(config.annualRatePercent).stripTrailingZeros().toPlainString()),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (!loan.isPaidOff) {
                config.nextPaymentDate()?.let { next ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_loan_next_payment), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(next.formatted(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                val payoff = schedule?.payoffDate
                if (payoff == null) {
                    Text(stringResource(R.string.loans_payment_too_small), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_loan_payoff), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(payoffMonthText(payoff), style = MaterialTheme.typography.bodyMedium)
                    }
                    DetailAmount(stringResource(R.string.account_loan_remaining_interest), schedule.totalInterest, hideDecimals)
                }
            }
        }
    }
}

@Composable
private fun DetailAmount(label: String, amount: Long, hideDecimals: Boolean, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
        Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * Whether an account's "Hide reconciled transactions" preference hides [transaction]. A status
 * chip (e.g. "Reconciled") supersedes the preference, matching ActualBudgetDatabase.fetchTransactions;
 * otherwise picking "Reconciled" while hiding reconciled rows would filter every result back out.
 */
internal fun hiddenAsReconciled(
    transaction: Transaction,
    statusFilter: TransactionStatusFilter,
    hideReconciled: Boolean,
): Boolean = hideReconciled && statusFilter == TransactionStatusFilter.ALL && transaction.reconciled

/** Card divider inset lining up with a transaction row's text, after its cleared indicator. */
private val TRANSACTION_DIVIDER_INSET = Spacing.screenHorizontal + 18.dp + 10.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(transaction: Transaction, hideDecimalPlaces: Boolean,
    showDate: Boolean, showAccount: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onClearedClick: (() -> Unit)? = null, tagColors: Map<String, String>? = null,
    selectionMode: Boolean = false, selected: Boolean = false, runningBalanceCents: Long? = null,
    showDivider: Boolean = true, isNew: Boolean = false) {
    val presentation = transactionRowPresentation(transaction, showAccount, transactionRowLabels())
    val effectiveTagColors = tagColors ?: rememberActualTagColors(transaction)
    val upcoming = transaction.isUpcoming
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (isNew) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) else Modifier)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        if (selectionMode && !upcoming) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
        val upcomingLabel = if (transaction.isMissed) stringResource(R.string.upcoming_missed)
        else stringResource(R.string.upcoming_upcoming)
        val upcomingColor = if (transaction.isMissed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        if (upcoming) {
            Icon(if (transaction.isMissed) Icons.Outlined.ErrorOutline else Icons.Outlined.Repeat,
                contentDescription = upcomingLabel, tint = upcomingColor,
                modifier = Modifier.padding(end = 10.dp, top = 2.dp).size(18.dp))
        } else {
            ClearedIndicator(transaction.cleared, onClearedClick, modifier = Modifier.padding(end = 10.dp, top = 2.dp),
                reconciled = transaction.reconciled)
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(presentation.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = if (upcoming) FontStyle.Italic else FontStyle.Normal),
                fontWeight = FontWeight.SemiBold,
                color = if (upcoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isNew) Text(stringResource(R.string.transactions_new_from_bank_sync), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            if (upcoming) {
                Spacer(Modifier.height(6.dp))
                Text(upcomingLabel, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = upcomingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                presentation.transferContext?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                } ?: Spacer(Modifier.height(7.dp))
                presentation.categoryLabel?.let { CategoryChip(it, transaction.type == Type.TRANSFER) }
            }
            if (transaction.notes.isNotBlank()) {
                Text(coloredTagText(transaction.notes, effectiveTagColors), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(transaction.amountCents, FontWeight.SemiBold, hideDecimalPlaces, muted = upcoming)
            val runningBalanceLabel = runningBalanceCents?.let { formatMoneyCents(it, hideDecimalPlaces) }
            val secondaryLine = runningBalanceLabel ?: presentation.accountLabel
            secondaryLine?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium,
                    color = if (runningBalanceLabel != null) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            } ?: Spacer(Modifier.height(7.dp))
            if (showDate) {
                Spacer(Modifier.height(if (secondaryLine != null) 5.dp else 0.dp))
                Text(formatTransactionDate(transaction.date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
    if (showDivider) HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

internal data class TransactionRowPresentation(
    val title: String,
    /** Null when there is nothing meaningful to show, e.g. an off-budget account with no category. */
    val categoryLabel: String?,
    val accountLabel: String?,
    val transferContext: String?,
)

/**
 * Row labels resolved once per composition site, so [transactionRowPresentation] stays a pure,
 * JVM-testable function while its display text still localizes.
 */
internal data class TransactionRowLabels(
    val unknownPayee: String,
    val uncategorized: String,
    val unknownAccount: String,
    val transferToFormat: String,
    val transferFromFormat: String,
    val transfer: String,
    val fromAccountFormat: String,
    val toAccountFormat: String,
)

@Composable
internal fun transactionRowLabels(): TransactionRowLabels = TransactionRowLabels(
    unknownPayee = stringResource(R.string.transaction_unknown_payee),
    uncategorized = stringResource(R.string.transaction_uncategorized),
    unknownAccount = stringResource(R.string.transaction_unknown_account),
    transferToFormat = stringResource(R.string.transaction_transfer_to),
    transferFromFormat = stringResource(R.string.transaction_transfer_from),
    transfer = stringResource(R.string.transaction_type_transfer),
    fromAccountFormat = stringResource(R.string.transaction_from_account),
    toAccountFormat = stringResource(R.string.transaction_to_account),
)

internal fun transactionRowPresentation(
    transaction: Transaction,
    showAccount: Boolean,
    labels: TransactionRowLabels,
): TransactionRowPresentation {
    if (transaction.type != Type.TRANSFER) {
        return TransactionRowPresentation(
            title = transaction.payee.ifBlank { labels.unknownPayee },
            // Actual never requires a category for off-budget accounts, so an empty category
            // there isn't "Uncategorized" the way it is for an on-budget transaction.
            categoryLabel = when {
                transaction.category.isNotBlank() -> transaction.category
                transaction.accountOffBudget -> null
                else -> labels.uncategorized
            },
            accountLabel = transaction.account.takeIf { showAccount && it.isNotBlank() },
            transferContext = null,
        )
    }
    val otherAccount = transaction.transferAccount?.ifBlank { null }
        ?: transaction.payee.ifBlank { labels.unknownAccount }
    val outgoing = transaction.amountCents < 0
    return TransactionRowPresentation(
        title = if (outgoing) labels.transferToFormat.format(otherAccount)
        else labels.transferFromFormat.format(otherAccount),
        categoryLabel = labels.transfer,
        accountLabel = null,
        transferContext = if (!showAccount) null
        else if (outgoing) labels.fromAccountFormat.format(transaction.account)
        else labels.toAccountFormat.format(transaction.account),
    )
}

// transactions must be newest-first (as loaded from Actual); reversing gives the chronological fold order.
internal fun accountRunningBalances(transactions: List<Transaction>, accountName: String): Map<String, Long> {
    var balance = 0L
    val balances = LinkedHashMap<String, Long>()
    for (transaction in transactions.asReversed()) {
        if (transaction.account != accountName) continue
        balance += transaction.amountCents
        balances[transaction.id] = balance
    }
    return balances
}

@Composable
private fun CategoryChip(label: String, transfer: Boolean) {
    Surface(
        // Rows now sit on surfaceContainer cards, so the category pill uses the next tone up.
        color = if (transfer) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = PillShape,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (transfer) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun ClearedIndicator(cleared: Boolean, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier,
    reconciled: Boolean = false) {
    // A reconciled row shows a lock, like Actual's register; tapping it asks to unlock (#992).
    Surface(
        modifier = modifier.size(18.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = if (cleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Icon(
            if (reconciled) Icons.Outlined.Lock else Icons.Rounded.Check,
            contentDescription = when {
                reconciled -> stringResource(R.string.transaction_reconciled)
                cleared -> stringResource(R.string.transaction_cleared)
                else -> stringResource(R.string.transaction_uncleared)
            },
            tint = if (cleared) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(
    transaction: Transaction,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit = {},
    tagColors: Map<String, String>? = null,
) {
    var confirmDelete by remember(transaction.id) { mutableStateOf(false) }
    val presentation = transactionRowPresentation(transaction, showAccount = true, transactionRowLabels())
    val effectiveTagColors = tagColors ?: rememberActualTagColors(transaction)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            Row(Modifier.fillMaxWidth().padding(end = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                ActuaSheetTitle(stringResource(R.string.transaction_details), Modifier.weight(1f))
                Amount(transaction.amountCents, FontWeight.Bold, hideDecimalPlaces)
                ClearedIndicator(transaction.cleared, modifier = Modifier.padding(start = Spacing.sm),
                    reconciled = transaction.reconciled)
            }
            ActuaSheetCard {
                Column(
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    TransactionDetail(stringResource(R.string.transaction_payee), presentation.title)
                    TransactionDetail(stringResource(R.string.transaction_date), formatTransactionDate(transaction.date))
                    presentation.categoryLabel?.let { TransactionDetail(stringResource(R.string.transaction_category), it) }
                    TransactionDetail(stringResource(R.string.transaction_account), transaction.account)
                    transaction.transferAccount?.takeIf(String::isNotBlank)?.let {
                        TransactionDetail(stringResource(R.string.transaction_transfer_account), it)
                    }
                    TransactionDetail(
                        stringResource(R.string.transaction_status),
                        if (transaction.cleared) stringResource(R.string.transaction_cleared)
                        else stringResource(R.string.transaction_uncleared),
                    )
                    transaction.notes.takeIf(String::isNotBlank)?.let {
                        TransactionTagDetail(stringResource(R.string.transaction_notes), it, effectiveTagColors)
                    }
                }
            }
            ActuaSheetCard {
                ActuaSheetAction(stringResource(R.string.action_edit), icon = Icons.Outlined.Edit, onClick = onEdit)
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.action_duplicate), icon = Icons.Outlined.ContentCopy, onClick = onDuplicate)
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.action_delete), icon = Icons.Outlined.DeleteOutline, destructive = true,
                    onClick = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.transaction_delete_question)) },
            text = { Text(stringResource(R.string.transaction_delete_message)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TransactionDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

@Composable
private fun TransactionTagDetail(label: String, value: String, tagColors: Map<String, String>) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(coloredTagText(value, tagColors), modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

internal fun formatTransactionDate(value: String): String {
    return formatStoredDate(value)
}

@Composable
private fun Amount(value: Long, weight: FontWeight, hideDecimalPlaces: Boolean, modifier: Modifier = Modifier, muted: Boolean = false) {
    val baseColor = if (value >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Text(formatMoneyCents(value, hideDecimalPlaces, showPositiveSign = true),
        style = AmountTypography.rowAmount.copy(fontWeight = weight, fontStyle = if (muted) FontStyle.Italic else FontStyle.Normal),
        textAlign = TextAlign.End,
        color = if (muted) baseColor.copy(alpha = 0.7f) else baseColor,
        modifier = modifier)
}

/** A text-only choice row in a picker sheet's card. */
@Composable
private fun Action(label: String, onClick: () -> Unit) {
    ActuaSheetAction(label, onClick = onClick)
}

private fun upcomingActionIcon(action: UpcomingTransactionAction): ImageVector = when (action) {
    UpcomingTransactionAction.POST -> Icons.Outlined.AddCircleOutline
    UpcomingTransactionAction.POST_TODAY -> Icons.Outlined.EventAvailable
    UpcomingTransactionAction.SKIP -> Icons.Outlined.SkipNext
    UpcomingTransactionAction.COMPLETE -> Icons.Outlined.CheckCircleOutline
}
