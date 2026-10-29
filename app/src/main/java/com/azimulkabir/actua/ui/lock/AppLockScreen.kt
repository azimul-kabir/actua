package com.azimulkabir.actua.ui.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.withResumed
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Shows [content] while unlocked. While [locked] the content is not composed at all, so no budget
 * data is drawn, held in the view hierarchy or readable by accessibility services behind the
 * lock screen. The system prompt opens once each time the app locks; after it is dismissed the
 * Unlock button asks again.
 */
@Composable
fun AppLockGate(locked: Boolean, onUnlocked: () -> Unit, content: @Composable () -> Unit) {
    if (!locked) {
        content()
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var message by remember { mutableStateOf<String?>(null) }
    var showSettingsShortcut by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    val promptTitle = stringResource(R.string.app_lock_prompt_unlock_title)
    val promptSubtitle = stringResource(R.string.app_lock_prompt_subtitle)
    val noScreenLock = stringResource(R.string.app_lock_error_no_screen_lock)

    fun requestUnlock() {
        val activity = context.findFragmentActivity() ?: return
        if (prompting) return
        showSettingsShortcut = false
        when (AppLockAuthenticator.availability(context)) {
            AppLockAuthenticator.Availability.AVAILABLE -> {
                prompting = true
                message = null
                AppLockAuthenticator.authenticate(activity, promptTitle, promptSubtitle) { result ->
                    prompting = false
                    when (result) {
                        AppLockAuthenticator.Result.Success -> onUnlocked()
                        is AppLockAuthenticator.Result.Error -> message = result.message.takeUnless { result.cancelled }
                    }
                }
            }
            // The device lost its screen lock after the app lock was turned on. Stay locked, and
            // say what to do, rather than opening the app for anyone who picks the phone up.
            AppLockAuthenticator.Availability.NO_SCREEN_LOCK -> {
                message = noScreenLock
                showSettingsShortcut = true
            }
            AppLockAuthenticator.Availability.UNAVAILABLE -> message = noScreenLock
        }
    }

    LaunchedEffect(Unit) { lifecycle.withResumed { requestUnlock() } }

    AppLockScreen(
        message = message,
        onUnlock = ::requestUnlock,
        onOpenSecuritySettings = if (showSettingsShortcut) {
            { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else null,
    )
}

/** The lock screen itself: stateless, so it can be tested and previewed on its own. */
@Composable
fun AppLockScreen(
    message: String?,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.app_lock_unlock)) }
            onOpenSecuritySettings?.let {
                TextButton(onClick = it) { Text(stringResource(R.string.app_lock_open_security_settings)) }
            }
        }
    }
}
