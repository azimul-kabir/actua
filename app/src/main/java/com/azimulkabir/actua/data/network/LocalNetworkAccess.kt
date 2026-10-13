package com.azimulkabir.actua.data.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Decides whether reaching the configured servers needs Android 17's ACCESS_LOCAL_NETWORK
 * permission (shown to users as "Nearby devices"), so public servers never trigger the prompt.
 */
object LocalNetworkAccess {
    fun isRequired(
        serverUrls: List<String>,
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): Boolean = serverUrls.mapNotNull(::hostOf).distinct().any { host ->
        // mDNS names resolve over the local network itself, so they always need access.
        if (host == "local" || host.endsWith(".local")) return@any true
        // An unresolvable host fails to connect with its own error; don't prompt for it.
        runCatching { resolve(host) }.getOrDefault(emptyList()).any(::isLocalAddress)
    }

    internal fun hostOf(serverUrl: String): String? {
        val trimmed = serverUrl.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        return runCatching { URI(withScheme).host }.getOrNull()
            ?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.')
            ?.takeIf(String::isNotEmpty)
    }

    /** Loopback stays on the device and is not restricted, so it is not a local-network address. */
    internal fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            // 100.64.0.0/10 carrier-grade NAT, used by Tailscale and similar overlays.
            is Inet4Address -> bytes[0] == 100 && bytes[1] in 64..127
            // fc00::/7 unique local addresses.
            is Inet6Address -> bytes[0] and 0xfe == 0xfc
            else -> false
        }
    }
}
