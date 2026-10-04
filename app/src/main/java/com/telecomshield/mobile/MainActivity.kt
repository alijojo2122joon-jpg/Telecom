package com.telecomshield.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Data + read-only network inspection (device's own interfaces only)
// ---------------------------------------------------------------------------

data class InterfaceInfo(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val mtu: Int,
    val ipv4: List<String>,
    val ipv6: List<String>
)

object NetworkInspector {
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

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val tabs = listOf("Dashboard", "Network", "Reports")
    var selected by remember { mutableIntStateOf(0) }

    Scaffold { inner ->
        Column(modifier = Modifier.padding(inner)) {
            TabRow(selectedTabIndex = selected) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = { Text(title) }
                    )
                }
            }
            when (selected) {
                0 -> DashboardScreen()
                1 -> NetworkScreen()
                else -> ReportsScreen()
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardScreen() {
    val ifaces by produceState(initialValue = emptyList<InterfaceInfo>()) {
        value = withContext(Dispatchers.IO) { NetworkInspector.listInterfaces() }
    }
    val active = ifaces.count { it.isUp && !it.isLoopback }
    val v4 = ifaces.sumOf { it.ipv4.size }
    val v6 = ifaces.sumOf { it.ipv6.size }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        SectionCard("Security status") {
            Text("Foundation build — passive, read-only observation only.")
            Spacer(Modifier.height(4.dp))
            Text(
                "No active validation modules are enabled in v0.1.",
                fontSize = 13.sp
            )
        }
        SectionCard("Network snapshot") {
            MetricRow("Interfaces detected", ifaces.size.toString())
            MetricRow("Active interfaces", active.toString())
            MetricRow("IPv4 addresses", v4.toString())
            MetricRow("IPv6 addresses", v6.toString())
        }
        SectionCard("Planned modules") {
            listOf(
                "Network path analysis",
                "Evidence engine",
                "Billing integrity validation",
                "Before/after verification",
                "Evidence hashing",
                "PDF / JSON reports"
            ).forEach { Text("•  $it", fontSize = 14.sp) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NetworkScreen() {
    val ifaces by produceState(initialValue = emptyList<InterfaceInfo>()) {
        value = withContext(Dispatchers.IO) { NetworkInspector.listInterfaces() }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        if (ifaces.isEmpty()) {
            SectionCard("Interfaces") { Text("No interfaces reported by the system.") }
        } else {
            ifaces.forEach { nif ->
                SectionCard("${nif.name}  ${if (nif.isUp) "● up" else "○ down"}") {
                    MetricRow("Loopback", if (nif.isLoopback) "yes" else "no")
                    MetricRow("MTU", if (nif.mtu >= 0) nif.mtu.toString() else "—")
                    if (nif.ipv4.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("IPv4", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        nif.ipv4.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                    }
                    if (nif.ipv6.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("IPv6", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        nif.ipv6.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReportsScreen() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        SectionCard("Reports") {
            Text("Report generation is not available in this foundation build.")
        }
        Spacer(Modifier.height(16.dp))
    }
}
