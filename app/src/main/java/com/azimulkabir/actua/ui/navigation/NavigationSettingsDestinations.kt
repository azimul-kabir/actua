package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.accounts.AccountsScreen
import com.azimulkabir.actua.ui.accounts.CloseAccountOptions
import com.azimulkabir.actua.ui.accounts.CloseCategoryGroup
import com.azimulkabir.actua.ui.categories.ManageCategoriesScreen
import com.azimulkabir.actua.ui.categories.ReorderGroupsScreen
import com.azimulkabir.actua.ui.accounts.ReorderAccountsScreen
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.home.MonthStartRule
import androidx.compose.runtime.getValue

@Composable
internal fun NavigationManageCategoriesScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    onReorderGroupsClick: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    reorders: NavigationReorders,
) = key(repository) {
    LaunchedEffect(repository, dataVersion) { reorders.queue.refresh() }
    val state = reorders.queue.view
    val groups = state.snapshot.categories
    ManageCategoriesScreen(
        reorderRevision = state.revision,
        savingOrder = state.saving,
        groups = groups,
        onBack = onBack,
        onReorderGroupsClick = onReorderGroupsClick,
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_group) { repository.createCategoryGroup(name) } },
        onRenameGroup = { group, name -> onMutate(R.string.navigation_renaming_group) { repository.renameCategoryGroup(group, name) } },
        onSetGroupHidden = { group, hidden ->
            onMutate(if (hidden) R.string.navigation_hiding_group else R.string.navigation_showing_group) { repository.setCategoryGroupHidden(group, hidden) }
        },
        onCreateCategory = { group, name -> onMutate(R.string.navigation_creating_category) { repository.createCategory(group, name) } },
        onRenameCategory = { group, category, name ->
            onMutate(R.string.navigation_renaming_category) { repository.renameCategory(group, category, name) }
        },
        onSetCategoryHidden = { group, category, hidden ->
            onMutate(if (hidden) R.string.navigation_hiding_category else R.string.navigation_showing_category) {
                repository.setCategoryHidden(group, category, hidden)
            }
        },
        onDeleteCategory = { group, category, transferToId ->
            onMutate(R.string.navigation_deleting_category) { repository.deleteCategory(group, category, transferToId) }
        },
        loadCategoryDeletePlan = { group, category ->
            repository.withBackgroundOperation { runCatching { repository.categoryDeletePlan(group, category) }.getOrNull() }
        },
        onDeleteGroup = { group, targetGroupId ->
            onMutate(R.string.navigation_deleting_group) { repository.deleteCategoryGroup(group.id, targetGroupId) }
        },
        onMoveCategory = reorders::category,
        onMoveCategoryToGroup = { reorders.category(it) },
        modifier = contentModifier,
    )
}

@Composable
internal fun NavigationReorderGroupsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    reorders: NavigationReorders,
) = key(repository) {
    LaunchedEffect(repository, dataVersion) { reorders.queue.refresh() }
    val state = reorders.queue.view
    val groups = state.snapshot.categories
    ReorderGroupsScreen(
        reorderRevision = state.revision,
        savingOrder = state.saving,
        groups = groups,
        onBack = onBack,
        onMoveGroup = reorders::categoryGroup,
        modifier = contentModifier,
    )
}

@Composable
internal fun NavigationReorderAccountsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    reorders: NavigationReorders,
) = key(repository) {
    LaunchedEffect(repository, dataVersion) { reorders.queue.refresh() }
    val state = reorders.queue.view
    val accounts = state.snapshot.accounts
    val groups = state.snapshot.accountGroups
    ReorderAccountsScreen(
        reorderRevision = state.revision,
        savingOrder = state.saving,
        accounts = accounts,
        groups = groups,
        onBack = onBack,
        onMoveAccount = reorders::account,
        onMoveGroup = reorders::accountGroup,
        onMoveGroupFromSheet = { reorders.accountGroup(it) },
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_account_group) { repository.createAccountGroup(name) } },
        onRenameGroup = { id, name -> onMutate(R.string.navigation_renaming_account_group) { repository.renameAccountGroup(id, name) } },
        onDeleteGroup = { id -> onMutate(R.string.navigation_deleting_account_group) { repository.deleteAccountGroup(id) } },
        modifier = contentModifier,
    )
}

@Composable
internal fun NavigationAccountsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    accounts: List<Account>,
    transactions: List<Transaction>,
    hideDecimalPlaces: Boolean,
    showMonthlySummary: Boolean,
    onShowMonthlySummaryChange: (Boolean) -> Unit,
    showAccountTypeIcons: Boolean,
    onShowAccountTypeIconsChange: (Boolean) -> Unit,
    creditCards: List<CreditCardStatus>,
    onAccountClick: (String) -> Unit,
    onAllAccountsClick: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    reorders: NavigationReorders,
    pendingCaptureCount: Int,
    onReviewCaptures: () -> Unit,
    newTransactionCounts: Map<String, Int>,
    onSearch: () -> Unit,
    onSetUpBankSync: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    favoriteAccountIds: Set<String>,
    onFavoriteAccountChange: (String, Boolean) -> Unit,
    onFavoriteFeedback: (String, Boolean) -> Unit,
    scrollToTopRequest: Int,
    hasFab: Boolean,
    monthStart: MonthStartRule,
) = key(repository) {
    LaunchedEffect(repository, dataVersion) { reorders.queue.refresh() }
    val state = reorders.queue.view
    val accountGroups = state.snapshot.accountGroups
    val accountsForReorder = state.snapshot.accounts
    AccountsScreen(
        reorderRevision = state.revision,
        savingOrder = state.saving,
        modifier = contentModifier,
        accounts = accounts,
        transactions = transactions,
        hideDecimalPlaces = hideDecimalPlaces,
        showMonthlySummary = showMonthlySummary,
        onShowMonthlySummaryChange = onShowMonthlySummaryChange,
        showAccountTypeIcons = showAccountTypeIcons,
        onShowAccountTypeIconsChange = onShowAccountTypeIconsChange,
        creditCards = creditCards,
        onAccountClick = onAccountClick,
        onAllAccountsClick = onAllAccountsClick,
        loadCloseOptions = { account ->
            repository.withBackgroundOperation {
                CloseAccountOptions(
                    hasTransactions = repository.accountHasTransactions(account.id),
                    categoryGroups = repository.categoryGroupsForReorder().map { group ->
                        CloseCategoryGroup(group.name, group.categories.map { it.id to it.name })
                    },
                )
            }
        },
        onCloseAccount = { account, transferAccountId, categoryId, forced ->
            onMutate(if (forced) R.string.navigation_force_closing_account else R.string.navigation_closing_account) {
                repository.closeAccount(account.id, transferAccountId, categoryId, forced)
            }
        },
        onReopenAccount = { account ->
            onMutate(R.string.navigation_reopening_account) { repository.reopenAccount(account.id) }
        },
        onRenameAccount = { account, name ->
            onMutate(R.string.navigation_renaming_account) { repository.renameAccount(account.id, name) }
        },
        onChangeAccountType = { account, type ->
            onMutate(R.string.navigation_changing_account_type) { repository.setAccountType(account.id, type) }
        },
        onCreateAccount = { name, offBudget, balance, type, groupId ->
            onMutate(R.string.navigation_creating_account) { repository.createAccount(name, offBudget, balance, type, groupId) }
        },
        accountGroups = accountGroups,
        onSetAccountGroup = { account, groupId ->
            onMutate(R.string.navigation_updating_account_group) { repository.setAccountGroup(account.id, groupId) }
        },
        accountsForReorder = accountsForReorder,
        onMoveAccount = reorders::account,
        onMoveGroup = reorders::accountGroup,
        onMoveGroupFromSheet = { reorders.accountGroup(it) },
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_account_group) { repository.createAccountGroup(name) } },
        onRenameGroup = { id, name -> onMutate(R.string.navigation_renaming_account_group) { repository.renameAccountGroup(id, name) } },
        onDeleteGroup = { id -> onMutate(R.string.navigation_deleting_account_group) { repository.deleteAccountGroup(id) } },
        pendingCaptureCount = pendingCaptureCount,
        onReviewCaptures = onReviewCaptures,
        newTransactionCounts = newTransactionCounts,
        onSearch = onSearch,
        onSetUpBankSync = onSetUpBankSync,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        favoriteAccountIds = favoriteAccountIds,
        onFavoriteAccountChange = onFavoriteAccountChange,
        onFavoriteFeedback = onFavoriteFeedback,
        scrollToTopRequest = scrollToTopRequest,
        hasFab = hasFab,
        monthStart = monthStart,
    )
}
