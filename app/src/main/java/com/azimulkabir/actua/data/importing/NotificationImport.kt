package com.azimulkabir.actua.data.importing

import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

object FinancialMessageParser {
    private val currencyAmount = Regex("(?i)(?:BDT|TK\\.?|USD|EUR|GBP|৳|\\$)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val labeledAmount = Regex("(?i)(?:amount|debited|credited|paid|spent|charged)[: ]+(?:BDT|TK\\.?|USD|EUR|GBP|৳|\\$)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val reference = Regex("(?i)(?:ref(?:erence)?|trx|txn|transaction)(?:\\s*(?:id|no))?[:# -]*([A-Z0-9-]{4,})")
    private val date = Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b")
    private val account = Regex("(?i)(?:card|a/c|account)[^0-9]{0,12}(?:x+|\\*+)?([0-9]{3,6})")
    val defaultProfile = FinancialMessageProfile()

    fun parse(text: String, source: String = "Shared text", today: LocalDate = LocalDate.now(),
        profile: FinancialMessageProfile = defaultProfile): ImportParseResult {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val lower = normalized.lowercase(Locale.ROOT)
        val debit = profile.debitKeywords.firstOrNull(lower::contains)
        val credit = profile.creditKeywords.firstOrNull(lower::contains)
        if (debit == null && credit == null) return failure("No debit or credit wording was recognized")
        val parsedAmount = (currencyAmount.find(normalized) ?: labeledAmount.find(normalized))?.groupValues?.get(1)
            ?.replace(",", "")?.toBigDecimalOrNull() ?: return failure("No transaction amount was recognized")
        val cents = parsedAmount.setScale(2).movePointRight(2).longValueExact() * if (debit != null) -1 else 1
        val parsedDate = date.find(normalized)?.destructured?.let { (day, month, year) ->
            runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
        } ?: today
        val merchant = counterparty(normalized, outgoing = debit != null) ?: source
        val ref = reference.find(normalized)?.groupValues?.get(1)
        val accountHint = account.find(normalized)?.groupValues?.get(1)
        val confidence = when {
            ref != null && merchant != source -> ImportConfidence.HIGH
            merchant != source -> ImportConfidence.MEDIUM
            else -> ImportConfidence.LOW
        }
        val encodedDate = parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth
        return ImportParseResult(listOf(ImportCandidate(1, encodedDate, merchant, "Imported from $source", cents,
            ref, confidence, source, accountHint)), emptyList())
    }

    private fun failure(message: String) = ImportParseResult(emptyList(), listOf(ImportProblem(1, message)))

    private val party = Regex("(?i)\\b(at|to|from)\\s+(.+?)(?=\\s+(?:at|to|from|on|ref|trx|txn|using|card|a/c|account)\\b|[.;]|$)")

    /** "your wallet", "card ending 1234", "account XX5678": where the money came from or went, not who. */
    private val fundingSource = Regex("(?i)^(?:(?:your|my)\\s+)?(?:wallet|balance|card|account|a/c)\\b.*")

    /**
     * The other party of a transaction (#1044): for money out the first "at"/"to" phrase, for money
     * in the first "from" phrase, skipping phrases that name the funding source; otherwise the first
     * remaining phrase. "Paid $5 to Starbucks from Wallet" is Starbucks, not Wallet.
     */
    internal fun counterparty(text: String, outgoing: Boolean): String? {
        val phrases = party.findAll(text)
            .map { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2].trim() }
            .filterNot { (_, phrase) -> phrase.isEmpty() || fundingSource.matches(phrase) }
            .toList()
        val preferred = if (outgoing) setOf("at", "to") else setOf("from")
        return (phrases.firstOrNull { it.first in preferred } ?: phrases.firstOrNull())?.second
    }
}

data class FinancialMessageProfile(
    val debitKeywords: Set<String> = setOf("debited", "debit", "purchase", "paid", "withdrawn", "withdrawal", "spent", "charged"),
    val creditKeywords: Set<String> = setOf("credited", "credit", "refund", "received", "deposited", "deposit"),
)

data class AutomationRejection(val reason: String, val atMillis: Long)

class NotificationImportPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("notification_import", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

    var allowedPackages: Set<String>
        get() = preferences.getStringSet("allowedPackages", emptySet())?.toSet().orEmpty()
        set(value) { preferences.edit().putStringSet("allowedPackages", value).apply() }

    var automationEnabled: Boolean
        get() = preferences.getBoolean("automationEnabled", false)
        set(value) {
            if (value && automationToken == null) regenerateAutomationToken()
            preferences.edit().putBoolean("automationEnabled", value).apply()
            clearAutomationRejection()
        }

    val automationToken: String?
        get() = preferences.getString("automationToken", null)

    fun regenerateAutomationToken(): String {
        val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        preferences.edit().putString("automationToken", token).apply()
        clearAutomationRejection()
        return token
    }

    /** Why the latest automation broadcast was dropped, and when; only a fixed reason is kept. */
    val automationRejection: AutomationRejection?
        get() = preferences.getString("automationRejection", null)?.let {
            AutomationRejection(it, preferences.getLong("automationRejectedAt", 0))
        }

    fun recordAutomationRejection(reason: String, atMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putString("automationRejection", reason).putLong("automationRejectedAt", atMillis).apply()
    }

    fun clearAutomationRejection() {
        if (preferences.contains("automationRejection")) {
            preferences.edit().remove("automationRejection").remove("automationRejectedAt").apply()
        }
    }

    /** Calls [onChange] when the rejection changes; invoke the returned function to stop. */
    fun observeAutomationRejection(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "automationRejection") onChange()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun profile(): FinancialMessageProfile = FinancialMessageProfile(
        preferences.getStringSet("debitKeywords", FinancialMessageParser.defaultProfile.debitKeywords)
            ?: FinancialMessageParser.defaultProfile.debitKeywords,
        preferences.getStringSet("creditKeywords", FinancialMessageParser.defaultProfile.creditKeywords)
            ?: FinancialMessageParser.defaultProfile.creditKeywords,
    )

    fun saveProfile(profile: FinancialMessageProfile) {
        preferences.edit().putStringSet("debitKeywords", profile.debitKeywords)
            .putStringSet("creditKeywords", profile.creditKeywords).apply()
    }

    /** Adds [candidate] to the front of the queue and returns it with its queue id. */
    fun enqueue(candidate: ImportCandidate): ImportCandidate {
        val stored = candidate.copy(queueId = candidate.queueId ?: UUID.randomUUID().toString())
        synchronized(NotificationImportPreferences::class.java) {
            writeQueue(queued().toMutableList().apply { add(0, stored) })
        }
        return stored
    }

    /** Removes the queued row [queueId] and returns it, or null when it is no longer queued. */
    fun removeQueued(queueId: String): ImportCandidate? = synchronized(NotificationImportPreferences::class.java) {
        val rows = queued()
        rows.firstOrNull { it.queueId == queueId }?.also { removed -> writeQueue(rows - removed) }
    }

    /** Whether captured rows also post a notification to review or import them (#1046). Off by default. */
    var confirmNotificationsEnabled: Boolean
        get() = preferences.getBoolean("confirmNotifications", false)
        set(value) { preferences.edit().putBoolean("confirmNotifications", value).apply() }

    /** Keeps only [candidates] queued, e.g. the rows an import left without an account (#1037). */
    fun replaceQueue(candidates: List<ImportCandidate>) {
        synchronized(NotificationImportPreferences::class.java) { writeQueue(candidates) }
    }

    private fun writeQueue(candidates: List<ImportCandidate>) {
        val json = JSONArray(candidates.take(100).map { JSONObject().put("id", it.queueId ?: UUID.randomUUID().toString())
            .put("date", it.date).put("payee", it.payee)
            .put("notes", it.notes).put("amount", it.amountCents).put("reference", it.reference)
            .put("confidence", it.confidence.name).put("source", it.sourceLabel).put("accountHint", it.accountHint) })
        preferences.edit().putString("queue", json.toString()).apply()
    }

    fun queued(): List<ImportCandidate> = runCatching {
        val array = JSONArray(preferences.getString("queue", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let { row -> ImportCandidate(
            index + 1, row.getInt("date"), row.getString("payee"), row.optString("notes"), row.getLong("amount"),
            row.optString("reference").takeIf { it.isNotBlank() && it != "null" },
            ImportConfidence.valueOf(row.getString("confidence")), row.getString("source"),
            row.optString("accountHint").takeIf { it.isNotBlank() && it != "null" },
            row.optString("id").takeIf(String::isNotBlank)) } }
    }.getOrDefault(emptyList())

    fun clearQueue() = preferences.edit().remove("queue").apply()

    /** Calls [onChange] when the review queue changes (#1050); invoke the returned function to stop. */
    fun observeQueue(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "queue") onChange()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** The account last imported for a captured row's hint or source in [budgetId] (#1045). */
    fun rememberedAccount(budgetId: String, key: String): String? =
        preferences.getString(accountMemoryKey(budgetId, key), null)

    fun rememberAccounts(budgetId: String, choices: Map<String, String>) {
        if (choices.isEmpty()) return
        preferences.edit().apply { choices.forEach { (key, id) -> putString(accountMemoryKey(budgetId, key), id) } }.apply()
    }

    private fun accountMemoryKey(budgetId: String, key: String) = "accountMemory:$budgetId:$key"
    // clear() only notifies listeners from API 30, so remove each key to keep the queue and
    // rejection observers current on API 28-29 without a second notification on newer releases.
    fun clearAll() = preferences.edit().apply { preferences.all.keys.forEach { remove(it) } }.apply()
}

class FinancialNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = NotificationImportPreferences(this)
        if (!shouldCaptureNotification(store.enabled, store.allowedPackages, sbn.packageName, packageName)) return
        val extras = sbn.notification.extras
        val content = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"))
            .joinToString(" ").trim()
        FinancialMessageParser.parse(content, notificationAppLabel(sbn.packageName), profile = store.profile()).candidates
            .forEach { CapturedTransactionNotifications.queue(this, store, it) }
    }
}

/** The app's name as the launcher shows it, so a payee-less alert reads "Chase", not its package. */
private fun Context.notificationAppLabel(sourcePackage: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(sourcePackage, 0)).toString()
}.getOrNull()?.takeIf(String::isNotBlank) ?: sourcePackage

internal fun shouldCaptureNotification(
    enabled: Boolean,
    allowedPackages: Set<String>,
    sourcePackage: String,
    ownPackage: String,
): Boolean = enabled && sourcePackage != ownPackage && sourcePackage in allowedPackages
