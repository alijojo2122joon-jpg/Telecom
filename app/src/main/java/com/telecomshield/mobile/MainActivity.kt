package com.telecomshield.mobile

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Red = Color(0xFFE53935)
private val Bg = Color(0xFF0A0A0A)
private val SurfaceC = Color(0xFF17191C)
private val VariantC = Color(0xFF202327)
private val OnC = Color(0xFFECECEC)
private val MutedC = Color(0xFF9AA0A6)

private val AmiriColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Bg,
    onBackground = OnC,
    surface = SurfaceC,
    onSurface = OnC,
    surfaceVariant = VariantC,
    onSurfaceVariant = MutedC
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AmiriColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        runCatching { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    }

    val tabs = listOf("Overview", "Speed", "Tools", "Usage")
    val icons = listOf(Icons.Filled.Dashboard, Icons.Filled.Speed, Icons.Filled.Build, Icons.Filled.DataUsage)
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(containerColor = Bg) { inner ->
        Column(modifier = Modifier.padding(inner)) {
            TabRow(selectedTabIndex = selected, containerColor = Bg, contentColor = Red) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = { Text(title, fontSize = 12.sp) },
                        icon = { Icon(icons[i], contentDescription = title, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
            when (selected) {
                0 -> OverviewScreen()
                1 -> SpeedScreen()
                2 -> ToolsScreen()
                else -> UsageScreen()
            }
        }
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceC)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Red)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = MutedC)
        Text(
            value,
            fontSize = if (strong) 16.sp else 14.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            color = OnC
        )
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_full),
            contentDescription = "Amiri Attack",
            modifier = Modifier.height(110.dp)
        )
    }
}

@Composable
private fun OverviewScreen() {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf<NetSnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = withContext(Dispatchers.IO) { NetMonitor.snapshot(ctx) }
            delay(2000)
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Header()
        val s = snap
        SectionCard("Connection") {
            Metric("Status", s?.connType ?: "…", strong = true)
            Metric("Carrier", s?.carrier ?: "…")
            Metric("Metered", if (s == null) "…" else if (s.isMetered) "Yes" else "No")
            Metric("IP address", s?.ipv4 ?: "…")
            if (s != null && s.downKbps > 0)
                Metric("Est. link down", "${s.downKbps / 1000} Mbps")
            if (s != null && s.upKbps > 0)
                Metric("Est. link up", "${s.upKbps / 1000} Mbps")
        }
        if (s?.connType == "Wi-Fi") {
            SectionCard("Wi-Fi") {
                Metric("Network", s.wifiSsid ?: "(hidden — allow location)")
                Metric("Signal", "${wifiLevel(s.wifiRssi)}  ${s.wifiRssi?.let { "($it dBm)" } ?: ""}")
                Metric("Link speed", s.wifiLinkMbps?.let { "$it Mbps" } ?: "—")
                Metric("Frequency", s.wifiFreqMhz?.let { "$it MHz" } ?: "—")
            }
        }
        SectionCard("Live data (since boot)") {
            Metric("Downloaded", formatBytes(s?.rxTotal ?: -1))
            Metric("Uploaded", formatBytes(s?.txTotal ?: -1))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SpeedScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var live by remember { mutableStateOf(0.0) }
    var down by remember { mutableStateOf<Double?>(null) }
    var up by remember { mutableStateOf<Double?>(null) }
    var ping by remember { mutableStateOf<PingResult?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        SectionCard("Speed test") {
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Red, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("  $phase", color = OnC, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(String.format("%.1f Mbps", live), color = Red, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            } else {
                Button(onClick = {
                    err = null; down = null; up = null; ping = null; live = 0.0
                    running = true
                    scope.launch {
                        try {
                            phase = "Pinging…"
                            val p = withContext(Dispatchers.IO) { SpeedTest.ping("1.1.1.1", 443) }
                            ping = p
                            phase = "Download…"
                            val d = withContext(Dispatchers.IO) {
                                SpeedTest.downloadMbps(25_000_000L) { live = it }
                            }
                            down = d; live = d
                            phase = "Upload…"
                            val u = withContext(Dispatchers.IO) { SpeedTest.uploadMbps(10_000_000L) }
                            up = u
                        } catch (e: Exception) {
                            err = e.message ?: "Test failed (no connection?)"
                        } finally {
                            running = false; phase = ""
                        }
                    }
                }) { Text("Start test") }
            }
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Red, fontSize = 13.sp)
            }
        }
        if (down != null || up != null || ping != null) {
            SectionCard("Results") {
                Metric("Download", down?.let { String.format("%.1f Mbps", it) } ?: "—", strong = true)
                Metric("Upload", up?.let { String.format("%.1f Mbps", it) } ?: "—", strong = true)
                ping?.let {
                    Metric("Ping (min)", String.format("%.0f ms", it.minMs))
                    Metric("Ping (avg)", String.format("%.0f ms", it.avgMs))
                    Metric("Jitter", String.format("%.0f ms", it.jitterMs))
                    if (it.failed > 0) Metric("Lost", "${it.failed}/${it.samples + it.failed}")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ToolsScreen() {
    val scope = rememberCoroutineScope()

    var pingHost by remember { mutableStateOf("google.com") }
    var pingOut by remember { mutableStateOf<String?>(null) }
    var pingBusy by remember { mutableStateOf(false) }

    var hostPort by remember { mutableStateOf("1.1.1.1") }
    var port by remember { mutableStateOf("443") }
    var checkOut by remember { mutableStateOf<String?>(null) }
    var checkBusy by remember { mutableStateOf(false) }

    var dnsHost by remember { mutableStateOf("github.com") }
    var dnsOut by remember { mutableStateOf<List<String>?>(null) }
    var dnsBusy by remember { mutableStateOf(false) }

    var ifaces by remember { mutableStateOf<List<InterfaceInfo>>(emptyList()) }
    LaunchedEffect(Unit) { ifaces = withContext(Dispatchers.IO) { NetMonitor.listInterfaces() } }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))

        SectionCard("Ping a host") {
            OutlinedTextField(
                value = pingHost, onValueChange = { pingHost = it },
                label = { Text("Host") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !pingBusy, onClick = {
                pingBusy = true; pingOut = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { SpeedTest.ping(pingHost.trim(), 443) }
                    pingOut = if (r.samples == 0) "Unreachable"
                    else String.format("avg %.0f ms · min %.0f ms · jitter %.0f ms · %d/%d ok",
                        r.avgMs, r.minMs, r.jitterMs, r.samples, r.samples + r.failed)
                    pingBusy = false
                }
            }) { Text(if (pingBusy) "Pinging…" else "Ping") }
            pingOut?.let { Spacer(Modifier.height(8.dp)); Text(it, color = OnC, fontSize = 13.sp) }
        }

        SectionCard("Host / port reachability") {
            OutlinedTextField(
                value = hostPort, onValueChange = { hostPort = it },
                label = { Text("Host") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !checkBusy, onClick = {
                checkBusy = true; checkOut = null
                scope.launch {
                    val p = port.toIntOrNull() ?: 80
                    val r = withContext(Dispatchers.IO) { SpeedTest.checkHostPort(hostPort.trim(), p) }
                    checkOut = if (r.reachable)
                        String.format("OPEN · %.0f ms · %s", r.ms, r.resolvedIp ?: "")
                    else "CLOSED / unreachable${r.resolvedIp?.let { "  ($it)" } ?: ""}"
                    checkBusy = false
                }
            }) { Text(if (checkBusy) "Checking…" else "Check") }
            checkOut?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (it.startsWith("OPEN")) Color(0xFF66BB6A) else Red, fontSize = 13.sp)
            }
        }

        SectionCard("DNS lookup") {
            OutlinedTextField(
                value = dnsHost, onValueChange = { dnsHost = it },
                label = { Text("Hostname") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !dnsBusy, onClick = {
                dnsBusy = true; dnsOut = null
                scope.launch {
                    dnsOut = withContext(Dispatchers.IO) { SpeedTest.dnsLookup(dnsHost.trim()) }
                    dnsBusy = false
                }
            }) { Text(if (dnsBusy) "Resolving…" else "Resolve") }
            dnsOut?.let { list ->
                Spacer(Modifier.height(8.dp))
                if (list.isEmpty()) Text("No records / not found", color = Red, fontSize = 13.sp)
                else list.forEach { Text(it, color = OnC, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            }
        }

        SectionCard("Interfaces") {
            if (ifaces.isEmpty()) Text("…", color = MutedC)
            else ifaces.forEach { nif ->
                Text(
                    "${nif.name}  ${if (nif.isUp) "● up" else "○ down"}",
                    color = OnC, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
                val ips = (nif.ipv4 + nif.ipv6).joinToString(", ").ifBlank { "no address" }
                Text("  $ips", color = MutedC, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UsageScreen() {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf<NetSnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = withContext(Dispatchers.IO) { NetMonitor.snapshot(ctx) }
            delay(1500)
        }
    }
    val s = snap
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        SectionCard("Total (since last boot)") {
            Metric("Downloaded", formatBytes(s?.rxTotal ?: -1), strong = true)
            Metric("Uploaded", formatBytes(s?.txTotal ?: -1), strong = true)
            Metric("Combined", formatBytes(((s?.rxTotal ?: 0) + (s?.txTotal ?: 0)).takeIf { s != null } ?: -1))
        }
        SectionCard("Mobile data (since last boot)") {
            Metric("Downloaded", formatBytes(s?.rxMobile ?: -1), strong = true)
            Metric("Uploaded", formatBytes(s?.txMobile ?: -1), strong = true)
            Spacer(Modifier.height(6.dp))
            Text(
                "Counters reset when the phone restarts. Mobile figures may be unavailable on some devices.",
                color = MutedC, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
