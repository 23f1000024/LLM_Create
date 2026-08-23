package com.unop2p.app.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/** A usable local address other phones on the same network can reach. */
data class LocalAddress(
    val ip: String,
    val interfaceName: String,
    val isIpv4: Boolean,
    val isLikelyWifi: Boolean,
) {
    fun hostPort(port: Int): String = if (isIpv4) "$ip:$port" else "[$ip]:$port"
}

/**
 * Enumerates the device's own network addresses so the host can show a join
 * address that peers can actually use. Loopback and link-local addresses are
 * excluded, and Wi-Fi-like interfaces are preferred over cellular/VPN.
 *
 * We never display 127.0.0.1 as a join address.
 */
object AddressUtils {

    private val WIFI_HINTS = listOf("wlan", "wifi", "ap", "eth")

    fun localAddresses(): List<LocalAddress> {
        val result = ArrayList<LocalAddress>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
            val name = nif.name?.lowercase().orEmpty()
            val isWifi = WIFI_HINTS.any { name.startsWith(it) }
            for (addr in nif.inetAddresses) {
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress) continue
                val host = addr.hostAddress ?: continue
                when (addr) {
                    is Inet4Address -> result += LocalAddress(host, nif.name, true, isWifi)
                    is Inet6Address -> {
                        // Strip any scope id (e.g. "%wlan0") for display.
                        val clean = host.substringBefore('%')
                        result += LocalAddress(clean, nif.name, false, isWifi)
                    }
                }
            }
        }
        // Prefer: IPv4 Wi-Fi, then any IPv4, then IPv6.
        return result.sortedWith(
            compareByDescending<LocalAddress> { it.isLikelyWifi && it.isIpv4 }
                .thenByDescending { it.isIpv4 }
                .thenByDescending { it.isLikelyWifi },
        )
    }

    /** The single best address to advertise, or null if the device is offline. */
    fun bestAddress(): LocalAddress? = localAddresses().firstOrNull()
}
