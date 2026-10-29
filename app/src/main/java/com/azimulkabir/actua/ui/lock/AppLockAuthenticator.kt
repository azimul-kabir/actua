package com.azimulkabir.actua.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper over [BiometricPrompt] for the app lock: a strong biometric or the device's own
 * PIN, pattern or password. It uses `androidx.biometric` only, never Play Services, so it works
 * in the F-Droid build.
 */
object AppLockAuthenticator {
    enum class Availability {
        AVAILABLE,

        /** No screen lock (and no enrolled biometric) on this device, so there is nothing to ask for. */
        NO_SCREEN_LOCK,

        /** Authentication can't be offered right now for another reason (no hardware, update pending). */
        UNAVAILABLE,
    }

    sealed interface Result {
        data object Success : Result

        /** Authentication ended without success. [cancelled] is true when the user backed out. */
        data class Error(val message: String, val cancelled: Boolean) : Result
    }

    /**
     * Android 9 and 10 can't combine a strong biometric with the device credential, so they accept a
     * weak one; Android 11+ requires strong, which is what the platform recommends for app locks.
     */
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun availability(context: Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(authenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NO_SCREEN_LOCK
            else -> Availability.UNAVAILABLE
        }

    /** Shows the system prompt; [onResult] is called once, on the main thread. */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onResult: (Result) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(Result.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(Result.Error(errString.toString(), cancelled))
            }
            // onAuthenticationFailed is a single non-matching attempt; the prompt stays open.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(authenticators())
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

/** The hosting [FragmentActivity], which [BiometricPrompt] needs, found through any context wrappers. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
