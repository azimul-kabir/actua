package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.network.LocalNetworkAccess

/**
 * When reaching a WebDAV server needs Android 17's Nearby devices permission (`ACCESS_LOCAL_NETWORK`): only on
 * Android 17 or later, only while it is not granted, and only for a server on the local network, as for Actual
 * servers. A public server never needs it.
 */
internal object WebDavLocalNetwork {
    /** The first Android version (17, API 37) that restricts apps from reaching the local network. */
    const val FIRST_RESTRICTED_SDK = 37

    /** True when [serverUrl] is on the local network. Resolves the host, so call it off the main thread. */
    fun isLocalAddress(serverUrl: String): Boolean = LocalNetworkAccess.isRequired(listOf(serverUrl))

    /** Whether to ask for the permission before connecting to [serverUrl]. */
    fun needsPermissionPrompt(
        serverUrl: String,
        sdkInt: Int,
        permissionGranted: Boolean,
        isLocal: (String) -> Boolean = ::isLocalAddress,
    ): Boolean = sdkInt >= FIRST_RESTRICTED_SDK && !permissionGranted && serverUrl.isNotBlank() && isLocal(serverUrl)

    /**
     * Whether [error] is most likely the missing permission rather than a bad address: the server could not be
     * reached, and it is on the local network, and Android 17 or later has not been given access to it.
     */
    fun explainsFailure(
        error: Throwable,
        serverUrl: String?,
        sdkInt: Int,
        permissionGranted: Boolean,
        isLocal: (String) -> Boolean = ::isLocalAddress,
    ): Boolean = error is WebDavException.Network && serverUrl != null &&
        needsPermissionPrompt(serverUrl, sdkInt, permissionGranted, isLocal)
}
