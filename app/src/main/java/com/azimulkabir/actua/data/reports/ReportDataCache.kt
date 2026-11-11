package com.azimulkabir.actua.data.reports

import java.lang.ref.SoftReference

/**
 * Keeps one budget's loaded data for a short time, so several widgets updating together load it once.
 *
 * A value is served only while its [Key] matches, which names the budget and a token that changes whenever
 * the budget's data does, and only for [ttlMillis] after it was loaded, as a backstop. It is held through a
 * soft reference, so it is released under memory pressure and costs nothing once it is no longer needed.
 *
 * The load runs under the lock, so callers on different threads that ask for the same data at the same
 * time wait for the one load instead of each starting their own. A load that fails is not cached, and does
 * not disturb what is already held.
 */
class ReportDataCache<T : Any>(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val reference: (T) -> SoftReference<T> = { SoftReference(it) },
) {
    /** Which data this is: the budget, and a [token] that differs whenever that budget's data has changed. */
    data class Key(val budgetId: String, val token: String)

    private class Entry<T : Any>(val key: Key, val loadedAtMillis: Long, val value: SoftReference<T>)

    private val lock = Any()
    private var entry: Entry<T>? = null

    /** The data for [key], loaded with [load] unless it is already held, current and still in memory. */
    fun get(key: Key, load: () -> T): T = synchronized(lock) {
        entry?.let { held ->
            if (held.key == key && nowMillis() - held.loadedAtMillis < ttlMillis) {
                held.value.get()?.let { return it }
            }
        }
        val loaded = load()
        entry = Entry(key, nowMillis(), reference(loaded))
        loaded
    }

    /** Forgets what is held, so the next [get] loads again. */
    fun clear() = synchronized(lock) { entry = null }

    companion object {
        /** Long enough for a wave of widget updates, short enough to bound how long anything undetected could linger. */
        const val DEFAULT_TTL_MILLIS = 120_000L
    }
}
