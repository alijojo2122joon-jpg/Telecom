package com.telecomshield.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

data class LanDevice(val ip: String, val host: String?)
data class WifiAp(val ssid: String, val rssiDbm: Int, val channel: Int, val security: String)

object Scanner {

    val COMMON_PORTS = linkedMapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB",
        3306 to "MySQL", 3389 to "RDP", 8080 to "HTTP-alt", 8443 to "HTTPS-alt"
    )

    fun subnetBase(ip: String?): String? {
        if (ip == null) return null
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}."
    }

    suspend fun lanScan(base: String, onProgress: (Int) -> Unit = {}): List<LanDevice> = coroutineScope {
        val sem = Semaphore(60)
        val done = AtomicInteger(0)
        val jobs = (1..254).map { i ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val ip = "$base$i"
                    val alive = reachable(ip)
                    onProgress(done.incrementAndGet())
                    if (alive) LanDevice(ip, reverse(ip)) else null
                }
            }
        }
        jobs.awaitAll().filterNotNull()
            .sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    private fun reachable(ip: String): Boolean {
        runCatching { if (InetAddress.getByName(ip).isReachable(250)) return true }
        for (p in intArrayOf(80, 443, 22, 139, 445)) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(ip, p), 180)
                return true
            } catch (_: Exception) {
            } finally {
                runCatching { s.close() }
            }
        }
        return false
    }

    private fun reverse(ip: String): String? = runCatching {
        val h = InetAddress.getByName(ip).canonicalHostName
        if (h == ip) null else h
    }.getOrNull()

    suspend fun servicePorts(host: String): List<String> = coroutineScope {
        val entries = COMMON_PORTS.entries.toList()
        val jobs = entries.map { e ->
            async(Dispatchers.IO) {
                val r = SpeedTest.checkHostPort(host, e.key, 1200)
                if (r.reachable) "${e.key}  ${e.value}" else null
            }
        }
        jobs.awaitAll().filterNotNull()
    }

    @SuppressLint("MissingPermission")
    fun nearbyWifi(ctx: Context): List<WifiAp> {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()
        val results = runCatching { wm.scanResults }.getOrNull() ?: return emptyList()
        return results.map {
            val ssid = it.SSID?.ifBlank { "(hidden)" } ?: "(hidden)"
            WifiAp(ssid, it.level, freqToChannel(it.frequency), security(it.capabilities))
        }.sortedByDescending { it.rssiDbm }
    }

    private fun freqToChannel(f: Int): Int = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2412) / 5 + 1
        f in 5170..5825 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> 0
    }

    private fun security(cap: String): String = when {
        cap.contains("WPA3") -> "WPA3"
        cap.contains("WPA2") -> "WPA2"
        cap.contains("WPA") -> "WPA"
        cap.contains("WEP") -> "WEP"
        else -> "Open"
    }

    fun publicIpInfo(): LinkedHashMap<String, String> {
        val c = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        try {
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(txt)
            return linkedMapOf(
                "Public IP" to j.optString("ip", "—"),
                "ISP / Org" to j.optString("org", "—"),
                "ASN" to j.optString("asn", "—"),
                "City" to j.optString("city", "—"),
                "Region" to j.optString("region", "—"),
                "Country" to j.optString("country_name", "—"),
                "Timezone" to j.optString("timezone", "—")
            )
        } finally {
            runCatching { c.disconnect() }
        }
    }
}
