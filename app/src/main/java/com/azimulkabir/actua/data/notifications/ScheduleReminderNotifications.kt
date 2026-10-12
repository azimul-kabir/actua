package com.azimulkabir.actua.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.preferences.BudgetDisplayFormats
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Device-local switch and lead time for scheduled-transaction reminders. Never synced. */
class ScheduleReminderSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("schedule_reminders", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) { preferences.edit().putBoolean(ENABLED, value).apply() }

    /** Days before the due date for the first reminder; one of [ScheduleReminderPlanner.leadDayOptions]. */
    var leadDays: Int
        get() = preferences.getInt(LEAD_DAYS, ScheduleReminderPlanner.DEFAULT_LEAD_DAYS)
            .takeIf { it in ScheduleReminderPlanner.leadDayOptions } ?: ScheduleReminderPlanner.DEFAULT_LEAD_DAYS
        set(value) { preferences.edit().putInt(LEAD_DAYS, value).apply() }

    internal var scheduledWorkNames: Set<String>
        get() = preferences.getStringSet(SCHEDULED_WORK, emptySet()).orEmpty().toSet()
        set(value) { preferences.edit().putStringSet(SCHEDULED_WORK, value).apply() }

    /** [ScheduleReminderPlanner.occurrenceKey]s of reminders currently posted, so settled ones can be withdrawn. */
    internal var postedOccurrences: Set<String>
        get() = preferences.getStringSet(POSTED, emptySet()).orEmpty().toSet()
        set(value) { preferences.edit().putStringSet(POSTED, value).apply() }

    private companion object {
        const val ENABLED = "enabled"
        const val LEAD_DAYS = "lead_days"
        const val SCHEDULED_WORK = "scheduled_work"
        const val POSTED = "posted"
    }
}

data class ScheduleReminder(
    val scheduleId: String,
    val dueDate: DayDate,
    val offsetDays: Int,
    val triggerAt: ZonedDateTime,
) {
    /** One name per occurrence and offset, so replanning never duplicates a reminder. */
    val workName: String get() = "actua-schedule-reminder-$scheduleId-${dueDate.yyyymmdd}-${offsetDays}d"
}

/** Launch contract for tapping a reminder: open the schedule, or the schedule list when it's gone. */
object ScheduleReminderLaunch {
    const val ACTION = "com.azimulkabir.actua.OPEN_SCHEDULE"
    const val EXTRA_SCHEDULE_ID = "scheduleId"

    /** The tapped schedule's id while it still exists in the open budget, else null. */
    fun scheduleId(schedules: List<ScheduleListItem>, scheduleId: String?): String? =
        scheduleId?.takeIf { id -> schedules.any { it.schedule.id == id } }
}

object ScheduleReminderPlanner {
    val leadDayOptions = listOf(0, 1, 3, 7)
    const val DEFAULT_LEAD_DAYS = 1
    private const val REMINDER_HOUR = 9

    /**
     * The occurrence a schedule reminds about: its persisted next date, while it is neither completed
     * nor paid. Posting, skipping or matching a transaction settles it (Actual advances `next_date`
     * or links the payment), so later reminders follow the new next date.
     */
    fun pendingDueDate(item: ScheduleListItem): DayDate? = item.schedule.nextDate
        ?.takeIf { item.status != ScheduleStatus.COMPLETED && item.status != ScheduleStatus.PAID }

    /** The lead-time reminder and, when it isn't already on the day, one on the due date. */
    fun offsets(leadDays: Int): List<Int> = if (leadDays == 0) listOf(0) else listOf(leadDays, 0)

    fun plan(
        schedules: List<ScheduleListItem>,
        leadDays: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): List<ScheduleReminder> = schedules.flatMap { item ->
        val dueDate = pendingDueDate(item) ?: return@flatMap emptyList()
        offsets(leadDays).mapNotNull { offset ->
            val day = dueDate.addingDays(-offset)
            val trigger = LocalDate.of(day.year, day.month, day.day).atTime(REMINDER_HOUR, 0).atZone(now.zone)
            trigger.takeIf { it.isAfter(now) }?.let { ScheduleReminder(item.schedule.id, dueDate, offset, it) }
        }
    }

    /** Whether a reminder planned for [dueDate], [offset] days ahead, should still post [today]. */
    fun stillDue(item: ScheduleListItem?, dueDate: DayDate, offset: Int, leadDays: Int, today: DayDate): Boolean =
        item != null && pendingDueDate(item) == dueDate && offset in offsets(leadDays) &&
            today.daysUntil(dueDate) == offset

    /** Posted occurrence keys whose occurrence is no longer pending, so their notifications should go. */
    fun settled(posted: Set<String>, schedules: List<ScheduleListItem>): Set<String> {
        val pending = schedules.mapNotNullTo(mutableSetOf()) { item ->
            pendingDueDate(item)?.let { occurrenceKey(item.schedule.id, it) }
        }
        return posted - pending
    }

    /** [posted] after posting the reminder for [scheduleId]'s [dueDate], which replaces any earlier one. */
    fun afterPosting(posted: Set<String>, scheduleId: String, dueDate: DayDate): Set<String> =
        posted.filterNotTo(mutableSetOf()) { scheduleIdOf(it) == scheduleId } + occurrenceKey(scheduleId, dueDate)

    fun title(name: String, dueDate: DayDate, today: DayDate): String = when (val days = today.daysUntil(dueDate)) {
        0 -> "$name is due today"
        1 -> "$name is due tomorrow"
        else -> "$name is due in $days days"
    }

    /** "−$45.00 · Checking · Due 10/15/2026"; [amount] is null when balances are hidden. */
    fun body(amount: String?, accountName: String?, date: String): String =
        listOfNotNull(amount, accountName?.takeIf(String::isNotBlank), "Due $date").joinToString(" · ")

    fun occurrenceKey(scheduleId: String, dueDate: DayDate) = "$scheduleId|${dueDate.yyyymmdd}"

    internal fun scheduleIdOf(occurrenceKey: String) = occurrenceKey.substringBeforeLast('|')

    /** One notification per schedule, so the due-date reminder replaces the lead-time one. */
    fun notificationId(scheduleId: String) = "schedule-reminder:$scheduleId".hashCode()
}

object ScheduleReminderScheduler {
    /**
     * Replans reminders for the open budget's schedules. Called after syncs, local edits and settings
     * changes; WorkManager keeps the planned work across process death, reboots and app updates.
     */
    fun refresh(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val app = context.applicationContext
        val settings = ScheduleReminderSettings(app)
        val manager = WorkManager.getInstance(app)
        val notifications = NotificationManagerCompat.from(app)
        if (!settings.isEnabled) {
            settings.scheduledWorkNames.forEach(manager::cancelUniqueWork)
            settings.scheduledWorkNames = emptySet()
            settings.postedOccurrences.forEach {
                notifications.cancel(ScheduleReminderPlanner.notificationId(ScheduleReminderPlanner.scheduleIdOf(it)))
            }
            settings.postedOccurrences = emptySet()
            return
        }

        val repository = ActuaRepository(app)
        val schedules = try {
            repository.schedules(DayDate.from(now.toLocalDate()))
        } finally { repository.close() }

        val posted = settings.postedOccurrences
        val settled = ScheduleReminderPlanner.settled(posted, schedules)
        settled.forEach {
            notifications.cancel(ScheduleReminderPlanner.notificationId(ScheduleReminderPlanner.scheduleIdOf(it)))
        }
        settings.postedOccurrences = posted - settled

        val reminders = ScheduleReminderPlanner.plan(schedules, settings.leadDays, now)
        val newNames = reminders.mapTo(mutableSetOf()) { it.workName }
        (settings.scheduledWorkNames - newNames).forEach(manager::cancelUniqueWork)
        reminders.forEach { reminder ->
            val delay = maxOf(0, Duration.between(now, reminder.triggerAt).toMillis())
            val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(reminder.toData())
                .build()
            // The name pins the occurrence and offset, so a pending reminder is kept as planned.
            manager.enqueueUniqueWork(reminder.workName, ExistingWorkPolicy.KEEP, request)
        }
        settings.scheduledWorkNames = newNames
    }

    private fun ScheduleReminder.toData() = Data.Builder()
        .putString(ScheduleReminderWorker.SCHEDULE_ID, scheduleId)
        .putInt(ScheduleReminderWorker.DUE_DATE, dueDate.yyyymmdd)
        .putInt(ScheduleReminderWorker.OFFSET_DAYS, offsetDays)
        .build()
}

class ScheduleReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = ScheduleReminderSettings(applicationContext)
        if (!settings.isEnabled || !notificationsAllowed(applicationContext)) return Result.success()
        val scheduleId = inputData.getString(SCHEDULE_ID) ?: return Result.failure()
        val dueDate = DayDate.fromYyyymmdd(inputData.getInt(DUE_DATE, 0)) ?: return Result.failure()
        val offset = inputData.getInt(OFFSET_DAYS, -1)

        val today = DayDate.today()
        val repository = ActuaRepository(applicationContext)
        var budgetFormats = BudgetDisplayFormats()
        val item = try {
            CurrencyDisplay.decimalPlaces = repository.budgetDecimalPlaces()
            budgetFormats = repository.budgetDisplayFormats()
            repository.schedules(today).firstOrNull { it.schedule.id == scheduleId }
        } finally { repository.close() }
        // Re-check against the budget as it is now: the occurrence may have been posted, skipped,
        // matched, edited or deleted since this reminder was planned.
        if (item == null || !ScheduleReminderPlanner.stillDue(item, dueDate, offset, settings.leadDays, today)) {
            return Result.success()
        }
        if (postNotification(applicationContext, item, dueDate, today, budgetFormats)) {
            settings.postedOccurrences =
                ScheduleReminderPlanner.afterPosting(settings.postedOccurrences, scheduleId, dueDate)
        }
        return Result.success()
    }

    companion object {
        const val SCHEDULE_ID = "scheduleId"
        const val DUE_DATE = "dueDate"
        const val OFFSET_DAYS = "offsetDays"
    }
}

private fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Posts the reminder; false when notifications aren't permitted. */
private fun postNotification(
    context: Context,
    item: ScheduleListItem,
    dueDate: DayDate,
    today: DayDate,
    budgetFormats: BudgetDisplayFormats,
): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) return false

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(
        CHANNEL_ID, "Scheduled transactions", NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = "Reminders before bills, income and other scheduled transactions are due" })
    val scheduleId = item.schedule.id
    val openSchedule = Intent(context, MainActivity::class.java).apply {
        action = ScheduleReminderLaunch.ACTION
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(ScheduleReminderLaunch.EXTRA_SCHEDULE_ID, scheduleId)
    }
    val notificationId = ScheduleReminderPlanner.notificationId(scheduleId)
    val contentIntent = PendingIntent.getActivity(
        context, notificationId, openSchedule,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val display = DisplayPreferences(context).resolve(budgetFormats)
    applyDisplayFormats(display)
    val date = formatDate(LocalDate.of(dueDate.year, dueDate.month, dueDate.day))
    // Hidden balances (privacy mode) keep the amount out of the notification entirely.
    val amount = if (display.hideBalances) null
    else formatMoneyCents(item.schedule.postAmount, display.hideDecimals, respectBalanceVisibility = false)
    val title = ScheduleReminderPlanner.title(item.title, dueDate, today)
    val body = ScheduleReminderPlanner.body(amount, item.accountName, date)
    // Shown on a secure lock screen that hides sensitive content: no payee, account or amount.
    val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.actua_launcher_monochrome)
        .setContentTitle("Scheduled transaction reminder")
        .setContentText("Unlock to see details")
        .build()
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.actua_launcher_monochrome)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(publicVersion)
        .setContentIntent(contentIntent).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
    return true
}

private const val CHANNEL_ID = "schedule-reminders"
