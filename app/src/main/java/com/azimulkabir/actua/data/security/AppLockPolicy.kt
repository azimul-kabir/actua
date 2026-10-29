package com.azimulkabir.actua.data.security

/** How long Actua may stay in the background before it asks for authentication again. */
enum class AppLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(5 * 60_000),
    FIFTEEN_MINUTES(15 * 60_000),
    ;

    companion object {
        val DEFAULT = IMMEDIATELY

        /** Unknown or missing stored values fall back to the strictest setting, never to "off". */
        fun fromStored(value: String?): AppLockTimeout = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Decides when the app is locked. It holds no Android types so the rules are unit-testable, and
 * lives for the whole process (held by `AppLock` in the UI layer): a new process always starts locked, while an
 * activity that is merely recreated (rotation, theme change) keeps whatever state it had.
 *
 * Call [onForeground] from `onStart` and [onBackground] from `onStop` (but not when the stop is
 * only a configuration change).
 */
class AppLockPolicy(private val clock: () -> Long) {
    var locked: Boolean = false
        private set

    private var started = false
    private var backgroundedAt: Long? = null

    fun onForeground(enabled: Boolean, timeout: AppLockTimeout) {
        if (!enabled) {
            locked = false
            started = true
            backgroundedAt = null
            return
        }
        if (!started) {
            // First start of this process: it may have been killed while the app was open.
            started = true
            locked = true
        } else {
            val since = backgroundedAt
            if (since != null && clock() - since >= timeout.millis) locked = true
        }
        backgroundedAt = null
    }

    fun onBackground() {
        backgroundedAt = clock()
    }

    /** Authentication succeeded. */
    fun unlock() {
        locked = false
    }

    /**
     * The setting was just turned on or off. Both require authentication first, so the app is
     * unlocked either way and only needs to start counting from the next time it is backgrounded.
     */
    fun onSettingChanged() {
        locked = false
        started = true
        backgroundedAt = null
    }
}
