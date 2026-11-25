package com.azimulkabir.actua.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.settings.SchedulesScreen
import com.azimulkabir.actua.ui.settings.FindSchedulesScreen
import com.azimulkabir.actua.ui.settings.BillsCalendarScreen
import com.azimulkabir.actua.ui.settings.ImportTransactionsScreen
import com.azimulkabir.actua.ui.settings.ManagePayeesScreen
import com.azimulkabir.actua.ui.settings.PayeeLocationsScreen
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.SchedulePrefill
import com.azimulkabir.actua.data.notifications.ScheduleReminderSettings

@Composable
internal fun NavigationScheduleScreens(
    repository: ActuaRepository,
    dataVersion: Int,
    schedules: List<ScheduleListItem>,
    schedulesLoaded: Boolean,
    nonWorkingDays: com.azimulkabir.actua.data.home.NonWorkingDays,
    accounts: List<Account>,
    payeeNames: List<String>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    context: Context,
    scheduleReminderSettings: ScheduleReminderSettings,
    scheduleReminderPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    contentModifier: Modifier,
    onMutate: (labelRes: Int, onChanged: () -> Unit, action: () -> Boolean) -> Unit,
    detail: DetailDestination,
    onDetailChange: (DetailDestination) -> Unit,
    editingScheduleId: String?,
    onEditingScheduleIdChange: (String?) -> Unit,
    scheduleReturnsToBills: Boolean,
    onScheduleReturnsToBillsChange: (Boolean) -> Unit,
    scheduleReturnsToTransactions: Boolean,
    onScheduleReturnsToTransactionsChange: (Boolean) -> Unit,
    scheduleReturnsToTransactionsTab: Boolean,
    onScheduleReturnsToTransactionsTabChange: (Boolean) -> Unit,
    billsCalendarReturnsToSchedules: Boolean,
    onBillsCalendarReturnsToSchedulesChange: (Boolean) -> Unit,
    creditCardsReturnToBills: Boolean,
    onCreditCardsReturnToBillsChange: (Boolean) -> Unit,
    scheduleRuleId: String?,
    onScheduleRuleIdChange: (String?) -> Unit,
    sharedImportText: String?,
    onSharedTextConsumed: () -> Unit,
    sharedImportFile: Uri?,
    onSharedFileConsumed: () -> Unit,
    capturedReviewRequest: Int,
    onReturnFromCapturedReview: () -> Unit,
    onOpenCapturedReview: (Boolean) -> Unit,
    onReturnFromEditSchedule: () -> Unit,
    schedulePrefill: SchedulePrefill?,
    onSchedulePrefillChange: (SchedulePrefill?) -> Unit,
    billsReturnDetail: DetailDestination,
    scheduleRemindersEnabled: Boolean,
    onScheduleRemindersEnabledChange: (Boolean) -> Unit,
    scheduleReminderLeadDays: Int,
    onScheduleReminderLeadDaysChange: (Int) -> Unit,
) {
    when (detail) {
        DetailDestination.Schedules -> SchedulesScreen(
            schedules = schedules,
            hideDecimalPlaces = hideDecimalPlaces,
            canAdd = accounts.any { !it.closed },
            onBack = { onDetailChange(DetailDestination.Main) },
            onAdd = {
                onSchedulePrefillChange(null)
                onScheduleReturnsToBillsChange(false)
                onDetailChange(DetailDestination.NewSchedule)
            },
            onFind = { onDetailChange(DetailDestination.FindSchedules) },
            onCalendar = {
                onBillsCalendarReturnsToSchedulesChange(true)
                onDetailChange(DetailDestination.BillsCalendar)
            },
            onEdit = { id ->
                onEditingScheduleIdChange(id)
                onScheduleReturnsToBillsChange(false)
                onScheduleReturnsToTransactionsChange(false)
                onScheduleReturnsToTransactionsTabChange(false)
                onDetailChange(DetailDestination.EditSchedule)
            },
            onPost = { id, today ->
                onMutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule, {}) {
                    repository.postScheduleTransaction(id, today, nonWorkingDays)
                }
            },
            onSkip = { id ->
                onMutate(R.string.navigation_skipping_next_date, {}) { repository.skipScheduleNextDate(id) }
            },
            onSetCompleted = { id, completed ->
                onMutate(if (completed) R.string.navigation_completing_schedule else R.string.navigation_restarting_schedule, {}) {
                    repository.setScheduleCompleted(id, completed)
                }
            },
            onDelete = { id ->
                onMutate(R.string.navigation_deleting_schedule, {}) { repository.deleteSchedule(id) }
            },
            remindersEnabled = scheduleRemindersEnabled,
            onRemindersEnabledChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                    scheduleReminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleReminderSettings.isEnabled = enabled
                    onScheduleRemindersEnabledChange(enabled)
                }
            },
            reminderLeadDays = scheduleReminderLeadDays,
            onReminderLeadDaysChange = { days ->
                scheduleReminderSettings.leadDays = days
                onScheduleReminderLeadDaysChange(days)
            },
            modifier = contentModifier,
        )
        DetailDestination.PayeeLocations -> {
        val locations = rememberLoaded(repository, dataVersion, initial = emptyList()) { repository.payeeLocations() }
        PayeeLocationsScreen(
            locations = locations,
            writesSupported = repository.payeeLocationWritesSupported(),
            onBack = { onDetailChange(DetailDestination.Main) },
            onDelete = { id ->
                onMutate(R.string.navigation_deleting_payee_location, {}) { repository.deletePayeeLocation(id) }
            },
            onClearPayee = { payeeId ->
                onMutate(R.string.navigation_clearing_payee_locations, {}) { repository.clearPayeeLocations(payeeId) > 0 }
            },
            modifier = contentModifier,
        )
        }
        DetailDestination.ManagePayees -> {
        val managedPayees = rememberLoaded(repository, dataVersion, initial = emptyList()) { repository.managedPayees() }
        val learnCategoriesEnabled = rememberLoaded(repository, dataVersion, initial = false) { repository.learnCategoriesEnabled() }
        ManagePayeesScreen(
            payees = managedPayees,
            learnCategoriesEnabled = learnCategoriesEnabled,
            onBack = { onDetailChange(DetailDestination.Main) },
            onSetLearnCategoriesEnabled = { enabled ->
                onMutate(R.string.navigation_changing_category_learning, {}) { repository.setLearnCategoriesEnabled(enabled) }
            },
            onRename = { payee, name -> onMutate(R.string.navigation_renaming_payee, {}) { repository.renamePayee(payee.id, name) } },
            onSetFavorite = { payee, favorite ->
                onMutate(R.string.navigation_updating_favorite, {}) { repository.setPayeeFavorite(payee.id, favorite) }
            },
            onSetLearnCategories = { payee, learn ->
                onMutate(R.string.navigation_changing_category_learning, {}) { repository.setPayeeLearnCategories(payee.id, learn) }
            },
            onDelete = { targets -> onMutate(R.string.navigation_deleting_payees, {}) { repository.deletePayees(targets.map { it.id }) } },
            onMerge = { target, merged ->
                onMutate(R.string.navigation_merging_payees, {}) { repository.mergePayees(target.id, merged.map { it.id }) }
            },
            modifier = contentModifier,
        )
        }
        DetailDestination.ImportTransactions -> ImportTransactionsScreen(
            accounts = accounts.filterNot { it.closed },
            findMatches = { accountId, candidates ->
                repository.withRetainedAccess { repository.importMatches(accountId, candidates) }
            },
            onImport = { groups, cleared, onImported ->
                onMutate(R.string.navigation_importing_transactions, onImported) {
                    repository.importTransactions(groups, cleared) == groups.sumOf { it.candidates.size }
                }
            },
            onBack = { onDetailChange(DetailDestination.Main) },
            modifier = contentModifier,
            initialSharedText = sharedImportText,
            onSharedTextConsumed = onSharedTextConsumed,
            initialSharedFile = sharedImportFile,
            onSharedFileConsumed = onSharedFileConsumed,
            onOpenCapturedReview = { onOpenCapturedReview(true) },
        )
        DetailDestination.ReviewCaptured -> key(capturedReviewRequest) {
            ImportTransactionsScreen(
                accounts = accounts.filterNot { it.closed },
                findMatches = { accountId, candidates ->
                    repository.withRetainedAccess { repository.importMatches(accountId, candidates) }
                },
                onImport = { groups, cleared, onImported ->
                    onMutate(R.string.navigation_importing_transactions, onImported) {
                        repository.importTransactions(groups, cleared) == groups.sumOf { it.candidates.size }
                    }
                },
                onBack = onReturnFromCapturedReview,
                modifier = contentModifier,
                capturedReviewOnly = true,
            )
        }
        DetailDestination.BillsCalendar -> BillsCalendarScreen(
            loadItems = { year, month, cardBills ->
                repository.billCalendarItems(year, month, cardBills, nonWorkingDays)
            },
            refreshKey = dataVersion,
            hideDecimalPlaces = hideDecimalPlaces,
            onBack = {
                onDetailChange(
                    if (billsCalendarReturnsToSchedules) {
                        onBillsCalendarReturnsToSchedulesChange(false)
                        DetailDestination.Schedules
                    } else {
                        DetailDestination.Main
                    },
                )
            },
            onAddSchedule = {
                onSchedulePrefillChange(null)
                onScheduleReturnsToBillsChange(true)
                onDetailChange(DetailDestination.NewSchedule)
            },
            onConfigureCards = {
                onCreditCardsReturnToBillsChange(true)
                onDetailChange(DetailDestination.CreditCards)
            },
            onEditSchedule = { id ->
                onEditingScheduleIdChange(id)
                onScheduleReturnsToBillsChange(true)
                onScheduleReturnsToTransactionsChange(false)
                onScheduleReturnsToTransactionsTabChange(false)
                onDetailChange(DetailDestination.EditSchedule)
            },
            onPost = { id, today ->
                onMutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule, {}) {
                    repository.postScheduleTransaction(id, today, nonWorkingDays)
                }
            },
            onSkip = { id -> onMutate(R.string.navigation_skipping_occurrence, {}) { repository.skipScheduleNextDate(id) } },
            onDelete = { id -> onMutate(R.string.navigation_deleting_schedule, {}) { repository.deleteSchedule(id) } },
            modifier = contentModifier,
        )
        DetailDestination.FindSchedules -> {
            val proposals by produceState<List<com.azimulkabir.actua.data.schedules.ScheduleDiscovery.DisplayProposal>?>(
                initialValue = null,
                key1 = dataVersion,
            ) {
                value = repository.withBackgroundOperation { repository.discoverSchedules() }
            }
            FindSchedulesScreen(
                proposals = proposals,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { onDetailChange(DetailDestination.Schedules) },
                onCreate = { selected ->
                    onMutate(
                        R.string.navigation_creating_schedules,
                        { onDetailChange(DetailDestination.Schedules) },
                    ) { repository.createDiscoveredSchedules(selected) }
                },
                modifier = contentModifier,
            )
        }
        DetailDestination.NewSchedule -> {
            val leaveNewSchedule = {
                onSchedulePrefillChange(null)
                if (scheduleReturnsToTransactions || scheduleReturnsToTransactionsTab) {
                    onReturnFromEditSchedule()
                } else {
                    onDetailChange(if (scheduleReturnsToBills) billsReturnDetail else DetailDestination.Schedules)
                    onScheduleReturnsToBillsChange(false)
                }
            }
            com.azimulkabir.actua.ui.settings.EditScheduleScreen(
                item = null,
                accounts = accounts,
                payeeOptions = payeeNames,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                prefill = schedulePrefill,
                onBack = leaveNewSchedule,
                onSave = { fields, payeeName ->
                    onMutate(R.string.navigation_creating_schedule, leaveNewSchedule) {
                        repository.createSchedule(fields, payeeName)
                    }
                },
                modifier = contentModifier,
            )
        }
        DetailDestination.EditSchedule -> schedules.firstOrNull {
            it.schedule.id == editingScheduleId
        }?.let { item ->
            val linkedTransactions = rememberLoaded<List<com.azimulkabir.actua.data.schedules.ScheduleLinkedTransaction>?>(
                repository, dataVersion, initial = null, selectionKey = item.schedule.id,
            ) { repository.scheduleTransactions(item.schedule.id) }
            com.azimulkabir.actua.ui.settings.EditScheduleScreen(
                item = item,
                accounts = accounts,
                payeeOptions = payeeNames,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                linkedTransactions = linkedTransactions.orEmpty(),
                linkedTransactionsLoading = linkedTransactions == null,
                onBack = onReturnFromEditSchedule,
                onSave = { fields, payeeName ->
                    onMutate(
                        R.string.navigation_saving_schedule,
                        onReturnFromEditSchedule,
                    ) { repository.updateSchedule(item.schedule.id, fields, payeeName) }
                },
                onDelete = {
                    onMutate(
                        R.string.navigation_deleting_schedule,
                        onReturnFromEditSchedule,
                    ) { repository.deleteSchedule(item.schedule.id) }
                },
                onUnlinkTransaction = { transactionId ->
                    onMutate(R.string.navigation_unlinking_transaction, {}) {
                        repository.unlinkScheduleTransaction(item.schedule.id, transactionId)
                    }
                },
                onEditAsRule = { ruleId ->
                    onScheduleRuleIdChange(ruleId)
                    onDetailChange(DetailDestination.Rules)
                },
                modifier = contentModifier,
            )
        } ?: run {
            if (!schedulesLoaded) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LaunchedEffect(editingScheduleId) { onDetailChange(DetailDestination.Schedules) }
            }
        }
        else -> Unit
    }
}
