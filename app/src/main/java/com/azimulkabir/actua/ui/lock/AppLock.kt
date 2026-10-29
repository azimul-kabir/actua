package com.azimulkabir.actua.ui.lock

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.security.AppLockPolicy
import com.azimulkabir.actua.data.security.AppLockTimeout

/**
 * The process-wide app-lock state. [locked] is observable and shared by every activity instance, so
 * an unlock that completes after the activity was recreated (a rotation while the system prompt is
 * open) still reaches the instance that is on screen. A new process starts with no memory of an
 * unlock, which is what makes a killed-and-restored app ask again.
 */
object AppLock {
    private val policy = AppLockPolicy(SystemClock::elapsedRealtime)

    var locked: Boolean by mutableStateOf(false)
        private set

    fun onForeground(enabled: Boolean, timeout: AppLockTimeout) {
        policy.onForeground(enabled, timeout)
        locked = policy.locked
    }

    /** Not for configuration-change stops: a rotation is not the user leaving the app. */
    fun onBackground() = policy.onBackground()

    fun unlock() {
        policy.unlock()
        locked = policy.locked
    }

    /** The setting was just turned on or off after authenticating. */
    fun onSettingChanged() {
        policy.onSettingChanged()
        locked = policy.locked
    }
}
