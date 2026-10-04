package com.telecomshield.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

data class NetSnapshot(
    val connType: String,
    val isMetered: Boolean,
    val downKbps: Int,
    val upKbps: Int,
    val carrier: String,
    val wifiSsid: String?,
    val wifiRssi: Int?,
    val wifiLinkMbps: Int?,
    val wifiFreqMhz: Int?,
    val ipv4: String?,
    val rxTotal: Long,
    val txTotal: Long,
    val rxMobile: Long,
    val txMobile: Long
)

data class InterfaceInfo(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val mtu: Int,
    val ipv4: List<String>,
    val ipv6: List<String>
)

object NetMonitor {

    @SuppressLint("MissingPermission")
    fun snapshot(ctx: Context): NetSnapshot {
        val app = ctx.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }

        val connType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        val metered = !(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: true)
        val down = caps?.linkDownstreamBandwidthKbps ?: 0
        val up = caps?.linkUpstreamBandwidthKbps ?: 0

        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrier = tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "—"

        var ssid: String? = null
        var rssi: Int? = null
        var link: Int? = null
        var freq: Int? = null
        var ip: String? = null

        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wm = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val info = wm?.connectionInfo
            if (info != null) {
                rssi = info.rssi
                link = info.linkSpeed
                freq = info.frequency
                val raw = info.ssid?.trim('"')
                ssid = if (raw.isNullOrBlank() || raw == "<unknown ssid>") null else raw
                @Suppress("DEPRECATION")
                val a = info.ipAddress
                if (a != 0) ip = String.format(
                    "%d.%d.%d.%d",
                    a and 0xff, a shr 8 and 0xff, a shr 16 and 0xff, a shr 24 and 0xff
                )
            }
        }
        if (ip == null) ip = firstIpv4()

        return NetSnapshot(
            connType = connType,
            isMetered = metered,
            downKbps = down,
            upKbps = up,
            carrier = carrier,
            wifiSsid = ssid,
            wifiRssi = rssi,
            wifiLinkMbps = link,
            wifiFreqMhz = freq,
            ipv4 = ip,
            rxTotal = TrafficStats.getTotalRxBytes(),
            txTotal = TrafficStats.getTotalTxBytes(),
            rxMobile = TrafficStats.getMobileRxBytes(),
            txMobile = TrafficStats.getMobileTxBytes()
        )
    }

    private fun firstIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    fun listInterfaces(): List<InterfaceInfo> {
        val out = mutableListOf<InterfaceInfo>()
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return out
        for (nif in ifaces) {
            val v4 = mutableListOf<String>()
            val v6 = mutableListOf<String>()
            for (addr in nif.inetAddresses) {
                when (addr) {
                    is Inet4Address -> addr.hostAddress?.let { v4.add(it) }
                    is Inet6Address -> addr.hostAddress?.let { v6.add(it.substringBefore('%')) }
                }
            }
            out.add(
                InterfaceInfo(
                    name = nif.name ?: "?",
                    isUp = runCatching { nif.isUp }.getOrDefault(false),
                    isLoopback = runCatching { nif.isLoopback }.getOrDefault(false),
                    mtu = runCatching { nif.mtu }.getOrDefault(-1),
                    ipv4 = v4,
                    ipv6 = v6
                )
            )
        }
        return out.sortedWith(
            compareByDescending<InterfaceInfo> { it.isUp && !it.isLoopback }.thenBy { it.name }
        )
    }
}

fun formatBytes(b: Long): String {
    if (b < 0) return "—"
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return if (i == 0) "${b} B" else String.format("%.2f %s", v, u[i])
}

fun wifiLevel(rssi: Int?): String = when {
    rssi == null -> "—"
    rssi >= -50 -> "Excellent"
    rssi >= -60 -> "Good"
    rssi >= -70 -> "Fair"
    rssi >= -80 -> "Weak"
    else -> "Very weak"
}
