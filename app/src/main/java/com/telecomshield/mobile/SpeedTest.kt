package com.telecomshield.mobile

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.max

data class PingResult(
    val minMs: Double,
    val avgMs: Double,
    val jitterMs: Double,
    val samples: Int,
    val failed: Int
)

data class HostCheck(val reachable: Boolean, val ms: Double, val resolvedIp: String?)

object SpeedTest {

    /** TCP-connect latency to host:port, repeated. */
    fun ping(host: String, port: Int = 443, count: Int = 6, timeoutMs: Int = 2000): PingResult {
        val times = mutableListOf<Double>()
        var failed = 0
        repeat(count) {
            val s = Socket()
            val t0 = System.nanoTime()
            try {
                s.connect(InetSocketAddress(host, port), timeoutMs)
                times.add((System.nanoTime() - t0) / 1e6)
            } catch (e: Exception) {
                failed++
            } finally {
                runCatching { s.close() }
            }
        }
        if (times.isEmpty()) return PingResult(0.0, 0.0, 0.0, 0, failed)
        val jitter = if (times.size > 1)
            times.zipWithNext { a, b -> abs(a - b) }.average() else 0.0
        return PingResult(times.min(), times.average(), jitter, times.size, failed)
    }

    /** Download [bytes] from Cloudflare and report throughput in Mbps. */
    fun downloadMbps(bytes: Long = 25_000_000L, onProgress: (Double) -> Unit = {}): Double {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        try {
            c.inputStream.use { ins ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val t0 = System.nanoTime()
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    val secs = (System.nanoTime() - t0) / 1e9
                    if (secs > 0.05) onProgress(total * 8 / 1e6 / secs)
                }
                val secs = max((System.nanoTime() - t0) / 1e9, 1e-6)
                return total * 8 / 1e6 / secs
            }
        } finally {
            runCatching { c.disconnect() }
        }
    }

    /** Upload [bytes] to Cloudflare and report throughput in Mbps. */
    fun uploadMbps(bytes: Long = 10_000_000L): Double {
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.setFixedLengthStreamingMode(bytes)
        val chunk = ByteArray(64 * 1024)
        val t0 = System.nanoTime()
        try {
            c.outputStream.use { os ->
                var sent = 0L
                while (sent < bytes) {
                    val n = minOf(chunk.size.toLong(), bytes - sent).toInt()
                    os.write(chunk, 0, n)
                    sent += n
                }
                os.flush()
            }
            c.responseCode
            val secs = max((System.nanoTime() - t0) / 1e9, 1e-6)
            return bytes * 8 / 1e6 / secs
        } finally {
            runCatching { c.disconnect() }
        }
    }

    /** Check whether a TCP port on a host accepts connections (diagnostic). */
    fun checkHostPort(host: String, port: Int, timeoutMs: Int = 3000): HostCheck {
        val s = Socket()
        val t0 = System.nanoTime()
        return try {
            val addr = InetSocketAddress(host, port)
            s.connect(addr, timeoutMs)
            HostCheck(true, (System.nanoTime() - t0) / 1e6, addr.address?.hostAddress)
        } catch (e: Exception) {
            HostCheck(false, 0.0, runCatching { InetAddress.getByName(host).hostAddress }.getOrNull())
        } finally {
            runCatching { s.close() }
        }
    }

    /** Resolve a hostname to its IP addresses. */
    fun dnsLookup(host: String): List<String> =
        runCatching {
            InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.filter { it.isNotBlank() }
        }.getOrElse { emptyList() }
}
