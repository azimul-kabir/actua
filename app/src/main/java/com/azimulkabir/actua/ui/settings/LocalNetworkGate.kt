package com.azimulkabir.actua.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.data.budget.webdav.WebDavLocalNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks for Android 17's Nearby devices permission before reaching a WebDAV server on the local network, the way
 * Connection does for Actual servers. [check] runs [onReady] straight away when no permission is needed (Android 16
 * or earlier, already granted, or a public server), or after the user allows it; [onDenied] runs if they do not.
 */
@Stable
class LocalNetworkGate internal constructor(
    val check: (serverUrl: String, onReady: () -> Unit, onDenied: () -> Unit) -> Unit,
)

@Composable
fun rememberLocalNetworkGate(): LocalNetworkGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callbacks = pending
        pending = null
        if (callbacks != null) {
            if (granted) callbacks.first() else callbacks.second()
        }
    }

    return LocalNetworkGate { serverUrl, onReady, onDenied ->
        val granted = Build.VERSION.SDK_INT < WebDavLocalNetwork.FIRST_RESTRICTED_SDK ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onReady()
        } else {
            scope.launch {
                val needed = withContext(Dispatchers.IO) {
                    WebDavLocalNetwork.needsPermissionPrompt(serverUrl, Build.VERSION.SDK_INT, permissionGranted = false)
                }
                if (needed) {
                    pending = onReady to onDenied
                    launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                } else {
                    onReady()
                }
            }
        }
    }
}
