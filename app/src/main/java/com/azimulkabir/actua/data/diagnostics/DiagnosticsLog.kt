package com.azimulkabir.actua.data.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A bounded, privacy-safe record of recent connection and sync events for bug reports (#225).
 *
 * Nothing sensitive can enter it: callers pass only typed values (an HTTP method, a server path,
 * a status code, a duration, counts, an exception's class), and every value is reduced here to a
 * fixed shape before it is stored. Server URLs and hosts, headers, tokens, passwords, keys, error
 * messages, budget names and transaction data are never accepted.
 *
 * Events live in memory and are mirrored to a private file once [attach] has been called, so a
 * failure in a background sync is still there when the user opens Diagnostics. The file keeps at
 * most [MAX_EVENTS] events from the last [RETENTION_DAYS] days, and [clear] deletes it.
 */
object DiagnosticsLog {
    const val MAX_EVENTS = 300
    const val RETENTION_DAYS = 7L
    private const val FILE_NAME = "diagnostics.log"

    data class Event(val atMillis: Long, val category: Category, val detail: String) {
        fun line(): String = "${timestamp(atMillis)} ${category.label} $detail"
    }

    enum class Category(val label: String) { HTTP("HTTP"), SYNC("SYNC"), BACKGROUND("BACKGROUND"), BANK_SYNC("BANK") }

    private val events = ArrayDeque<Event>()
    private var file: File? = null
    private var appendedSinceTrim = 0
    internal var clock: () -> Long = System::currentTimeMillis

    /** Starts mirroring to (and loads earlier events from) the app's private storage. */
    @Synchronized
    fun attach(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        val pending = events.toList()
        events.clear()
        runCatching { target.takeIf(File::exists)?.readLines().orEmpty() }.getOrDefault(emptyList())
            .mapNotNull(::parse).forEach(events::addLast)
        pending.forEach(events::addLast)
        prune()
        rewrite()
    }

    @Synchronized
    fun events(): List<Event> {
        prune()
        return events.toList()
    }

    @Synchronized
    fun clear() {
        events.clear()
        runCatching { file?.delete() }
    }

    /** A request to the Actual server: method, path shape, status (or failure class) and duration. */
    fun http(method: String, path: String, status: Int?, durationMillis: Long, error: Throwable? = null) {
        val outcome = status?.toString() ?: "failed ${errorType(error)}"
        record(Category.HTTP, "${safeToken(method.uppercase(), 8)} ${pathShape(path)} $outcome ${durationMillis}ms")
    }

    fun syncStarted(trigger: String) = record(Category.SYNC, "started (${safeLabel(trigger)})")

    fun syncSucceeded(sentMessages: Int, receivedMessages: Int, passes: Int, durationMillis: Long) =
        record(Category.SYNC, "ok sent=$sentMessages received=$receivedMessages passes=$passes ${durationMillis}ms")

    fun syncFailed(error: Throwable, durationMillis: Long) =
        record(Category.SYNC, "failed ${errorType(error)} ${durationMillis}ms")

    /** Why a sync didn't run at all, from a fixed set of reasons. */
    fun syncSkipped(reason: String) = record(Category.SYNC, "skipped (${safeLabel(reason)})")

    fun background(outcome: String) = record(Category.BACKGROUND, safeLabel(outcome))

    /** A bank-sync run: counts and each account's stored status code, never account names. */
    fun bankSync(accounts: Int, imported: Int, matched: Int, updated: Int, statuses: List<String>) {
        val byStatus = statuses.map { it.takeIf(KNOWN_BANK_STATUSES::contains) ?: "other" }.groupingBy { it }.eachCount()
            .entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "none" }
        record(Category.BANK_SYNC, "accounts=$accounts imported=$imported matched=$matched updated=$updated statuses=$byStatus")
    }

    fun bankSyncFailed(error: Throwable) = record(Category.BANK_SYNC, "failed ${errorType(error)}")

    /** Actual's `bank_sync_status` values; anything else is logged as "other". */
    private val KNOWN_BANK_STATUSES = setOf(
        "ok", "failed", "timed-out", "account-missing", "reauth-required", "attention-required",
        "rate-limit-exceeded", "none",
    )

    @Synchronized
    private fun record(category: Category, detail: String) {
        val event = Event(clock(), category, detail)
        events.addLast(event)
        while (events.size > MAX_EVENTS) events.removeFirst()
        val target = file ?: return
        runCatching { target.appendText(event.line() + "\n") }
        if (++appendedSinceTrim >= MAX_EVENTS) rewrite()
    }

    private fun prune() {
        val cutoff = clock() - RETENTION_DAYS * 24 * 60 * 60 * 1000
        while (events.isNotEmpty() && (events.first().atMillis < cutoff || events.size > MAX_EVENTS)) events.removeFirst()
    }

    private fun rewrite() {
        appendedSinceTrim = 0
        val target = file ?: return
        runCatching { target.writeText(events.joinToString("") { it.line() + "\n" }) }
    }

    private fun parse(line: String): Event? {
        val parts = line.split(' ', limit = 3)
        if (parts.size < 3) return null
        val at = runCatching { Instant.parse(parts[0]).toEpochMilli() }.getOrNull() ?: return null
        val category = Category.entries.firstOrNull { it.label == parts[1] } ?: return null
        return Event(at, category, parts[2])
    }

    private val formatter = DateTimeFormatter.ISO_INSTANT

    internal fun timestamp(millis: Long): String =
        formatter.format(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).withNano(0))

    /**
     * A server path reduced to its shape: query strings and fragments dropped, and any segment that
     * isn't a plain lowercase word (ids, file names, secret names, tokens) replaced with `:id`.
     */
    internal fun pathShape(path: String): String {
        val clean = path.substringBefore('?').substringBefore('#')
        val segments = clean.split('/').filter(String::isNotEmpty).map { segment ->
            if (segment.length <= 32 && segment.all { it in 'a'..'z' || it == '-' || it == '_' }) segment else ":id"
        }
        return "/" + segments.joinToString("/")
    }

    /** An exception's class names only (with an HTTP status where the type carries one), never its message. */
    internal fun errorType(error: Throwable?): String {
        if (error == null) return "Unknown"
        val chain = generateSequence(error) { it.cause }.take(3).map { cause ->
            val status = (cause as? com.azimulkabir.actua.data.network.ActualServerException.Http)?.status
            safeToken(cause.javaClass.simpleName.ifEmpty { "Error" }, 64) + (status?.let { "($it)" } ?: "")
        }
        return chain.joinToString("<-")
    }

    /** Letters, digits, spaces and a few separators, at most 40 characters. */
    internal fun safeLabel(value: String): String =
        value.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.take(40).trim().ifEmpty { "unknown" }

    private fun safeToken(value: String, max: Int): String =
        value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(max).ifEmpty { "unknown" }
}
