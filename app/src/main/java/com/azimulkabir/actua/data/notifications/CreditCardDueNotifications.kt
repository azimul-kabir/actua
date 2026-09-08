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
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.CreditCardStatus
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.concurrent.TimeUnit

class CreditCardNotificationSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("credit_card_notifications", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) { preferences.edit().putBoolean(ENABLED, value).apply() }

    internal var scheduledWorkNames: Set<String>
        get() = preferences.getStringSet(SCHEDULED_WORK, emptySet()).orEmpty().toSet()
        set(value) { preferences.edit().putStringSet(SCHEDULED_WORK, value).apply() }

    private companion object {
        const val ENABLED = "enabled"
        const val SCHEDULED_WORK = "scheduled_work"
    }
}

data class CreditCardReminder(
    val accountId: String,
    val dueDate: DayDate,
    val offsetDays: Int,
    val triggerAt: ZonedDateTime,
) {
    val workName: String get() = "actua-credit-card-due-$accountId-${offsetDays}d"
}

object CreditCardReminderPlanner {
    val reminderOffsets = listOf(7, 5, 3, 1)

    fun plan(cards: List<CreditCardStatus>, now: ZonedDateTime = ZonedDateTime.now()): List<CreditCardReminder> {
        val today = DayDate.from(now.toLocalDate())
        return cards.asSequence()
            .filter { !it.closed && it.balanceCents < 0 }
            .flatMap { card ->
                val dueDate = card.cycle.upcomingDueDate(today)
                reminderOffsets.asSequence().mapNotNull { offset ->
                    val reminderDay = dueDate.addingDays(-offset)
                    val trigger = LocalDate.of(reminderDay.year, reminderDay.month, reminderDay.day)
                        .atTime(9, 0).atZone(now.zone)
                    trigger.takeIf { it.isAfter(now) }?.let {
                        CreditCardReminder(card.accountId, dueDate, offset, it)
                    }
                }
            }.toList()
    }
}

object CreditCardDueNotificationScheduler {
    fun refresh(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val app = context.applicationContext
        val settings = CreditCardNotificationSettings(app)
        val manager = WorkManager.getInstance(app)
        if (!settings.isEnabled) {
            settings.scheduledWorkNames.forEach(manager::cancelUniqueWork)
            settings.scheduledWorkNames = emptySet()
            return
        }

        val repository = ActuaRepository(app)
        val cards = try { repository.creditCards() } finally { repository.close() }
        val reminders = CreditCardReminderPlanner.plan(cards, now)
        val newNames = reminders.mapTo(mutableSetOf()) { it.workName }
        (settings.scheduledWorkNames - newNames).forEach(manager::cancelUniqueWork)
        reminders.forEach { reminder ->
            val delay = maxOf(0, Duration.between(now, reminder.triggerAt).toMillis())
            val request = OneTimeWorkRequestBuilder<CreditCardDueNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(reminder.toData())
                .build()
            manager.enqueueUniqueWork(reminder.workName, ExistingWorkPolicy.REPLACE, request)
        }
        settings.scheduledWorkNames = newNames
    }

    private fun CreditCardReminder.toData() = Data.Builder()
        .putString(CreditCardDueNotificationWorker.ACCOUNT_ID, accountId)
        .putInt(CreditCardDueNotificationWorker.DUE_DATE, dueDate.yyyymmdd)
        .putInt(CreditCardDueNotificationWorker.OFFSET_DAYS, offsetDays)
        .build()
}

class CreditCardDueNotificationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = CreditCardNotificationSettings(applicationContext)
        if (!settings.isEnabled || !notificationsAllowed(applicationContext)) return Result.success()
        val accountId = inputData.getString(ACCOUNT_ID) ?: return Result.failure()
        val dueDate = DayDate.fromYyyymmdd(inputData.getInt(DUE_DATE, 0)) ?: return Result.failure()
        val offset = inputData.getInt(OFFSET_DAYS, 0)
        if (offset !in CreditCardReminderPlanner.reminderOffsets) return Result.failure()

        val repository = ActuaRepository(applicationContext)
        val card = try { repository.creditCards().firstOrNull { it.accountId == accountId } }
            finally { repository.close() }
        val today = DayDate.today()
        if (card == null || card.closed || card.balanceCents >= 0 ||
            card.cycle.upcomingDueDate(today) != dueDate || today.daysUntil(dueDate) != offset) {
            return Result.success()
        }
        postNotification(applicationContext, card, dueDate, offset)
        return Result.success()
    }

    companion object {
        const val ACCOUNT_ID = "accountId"
        const val DUE_DATE = "dueDate"
        const val OFFSET_DAYS = "offsetDays"
    }
}

private fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun postNotification(context: Context, card: CreditCardStatus, dueDate: DayDate, offset: Int) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) return

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(
        CHANNEL_ID, "Credit card due dates", NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Payment reminders for tracked credit cards" })
    val openApp = PendingIntent.getActivity(
        context, card.accountId.hashCode(), Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val display = DisplayPreferences(context)
    val amount = NumberFormat.getCurrencyInstance().apply {
        runCatching { currency = Currency.getInstance(display.currencyCode) }
        maximumFractionDigits = if (display.hideDecimalPlaces) 0 else currency.defaultFractionDigits
    }.format(kotlin.math.abs(card.balanceCents) / 100.0)
    val date = LocalDate.of(dueDate.year, dueDate.month, dueDate.day)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val whenText = if (offset == 1) "tomorrow" else "in $offset days"
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.actua_launcher_monochrome)
        .setContentTitle("${card.accountName} payment due $whenText")
        .setContentText("Current balance $amount. Payment due $date.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Current balance $amount. Payment due $date."))
        .setContentIntent(openApp).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()
    NotificationManagerCompat.from(context).notify(card.accountId.hashCode() * 31 + offset, notification)
}

private const val CHANNEL_ID = "credit-card-due"
