package com.azimulkabir.actua.data.importing

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

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
        val merchant = Regex("(?i)(?:at|to|from)\\s+(.+?)(?=\\s+(?:at|to|from|on|ref|trx|txn|using|card|a/c|account)\\b|[.;]|$)")
            .findAll(normalized).lastOrNull()?.groupValues?.get(1)?.trim()
            ?: source
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
}

data class FinancialMessageProfile(
    val debitKeywords: Set<String> = setOf("debited", "debit", "purchase", "paid", "withdrawn", "withdrawal", "spent", "charged"),
    val creditKeywords: Set<String> = setOf("credited", "credit", "refund", "received", "deposited", "deposit"),
)

class NotificationImportPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("notification_import", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

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

    fun enqueue(candidate: ImportCandidate) {
        synchronized(NotificationImportPreferences::class.java) {
            val rows = queued().toMutableList().apply { add(0, candidate) }.take(100)
            val json = JSONArray(rows.map { JSONObject().put("date", it.date).put("payee", it.payee)
                .put("notes", it.notes).put("amount", it.amountCents).put("reference", it.reference)
            .put("confidence", it.confidence.name).put("source", it.sourceLabel).put("accountHint", it.accountHint) })
            preferences.edit().putString("queue", json.toString()).apply()
        }
    }

    fun queued(): List<ImportCandidate> = runCatching {
        val array = JSONArray(preferences.getString("queue", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let { row -> ImportCandidate(
            index + 1, row.getInt("date"), row.getString("payee"), row.optString("notes"), row.getLong("amount"),
            row.optString("reference").takeIf { it.isNotBlank() && it != "null" },
            ImportConfidence.valueOf(row.getString("confidence")), row.getString("source"),
            row.optString("accountHint").takeIf { it.isNotBlank() && it != "null" }) } }
    }.getOrDefault(emptyList())

    fun clearQueue() = preferences.edit().remove("queue").apply()
    fun clearAll() = preferences.edit().clear().apply()
}

class FinancialNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = NotificationImportPreferences(this)
        if (!store.enabled || sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val content = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"))
            .joinToString(" ").trim()
        FinancialMessageParser.parse(content, sbn.packageName, profile = store.profile()).candidates.forEach(store::enqueue)
    }
}
