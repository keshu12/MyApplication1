package com.example.myapplication1

import android.net.TrafficStats
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.myapplication1.ui.theme.MyApplication1Theme
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Collections

// --- 1. 数据模型 ---
data class IpAddressInfo(val type: String, val address: String)
data class IpLocation(val city: String = "", val isp: String = "")
data class NetworkCardInfo(
    val displayName: String,
    val name: String,
    val isUp: Boolean,
    val ipAddresses: List<IpAddressInfo>,
    var pingResult: String = ""
)

// --- 2. 逻辑工具类 ---
object NetworkTools {
    fun fetchNetworkInterfaces(): List<NetworkCardInfo> {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces.mapNotNull { intf ->
                val ipInfos = Collections.list(intf.inetAddresses)
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { addr ->
                        IpAddressInfo(
                            type = if (addr is Inet6Address) "IPv6" else "IPv4",
                            address = addr.hostAddress?.split('%')?.get(0) ?: "Unknown"
                        )
                    }
                if (ipInfos.isEmpty()) return@mapNotNull null
                NetworkCardInfo(intf.displayName, intf.name, intf.isUp, ipInfos)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun pingHost(host: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var result = "Timeout"
            reader.useLines { lines ->
                lines.forEach { if (it.contains("time=")) result = it.substringAfter("time=").substringBefore(" ") + " ms" }
            }
            process.waitFor()
            result
        } catch (e: Exception) { "Error" }
    }

    suspend fun getIpLocation(ip: String): IpLocation = withContext(Dispatchers.IO) {
        try {
            val text = URL("http://ip-api.com/json/$ip").readText()
            val city = text.substringAfter("\"city\":\"").substringBefore("\"")
            val isp = text.substringAfter("\"isp\":\"").substringBefore("\"")
            IpLocation(city, isp)
        } catch (e: Exception) { IpLocation("Unknown", "N/A") }
    }

    suspend fun scanLocalNetwork(localIp: String): List<String> = withContext(Dispatchers.IO) {
        val foundDevices = Collections.synchronizedList(mutableListOf<String>())
        val prefix = localIp.substringBeforeLast(".")
        (1..254).map { i ->
            launch {
                val testIp = "$prefix.$i"
                try {
                    if (java.net.InetAddress.getByName(testIp).isReachable(400)) foundDevices.add(testIp)
                } catch (e: Exception) { }
            }
        }.joinAll()
        foundDevices.sorted()
    }
}

// --- 3. UI 组件 ---

@Composable
fun SpeedChart(history: List<Float>) {
    val maxSpeed = (history.maxOrNull() ?: 1f).coerceAtLeast(100f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("实时速率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${history.lastOrNull()?.toInt() ?: 0} KB/s", color = primaryColor, fontSize = 12.sp)
        }

        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepX = size.width / (if (history.size > 1) history.size - 1 else 1)

                // 1. 绘制渐变填充区域
                val fillPath = Path().apply {
                    moveTo(0f, size.height)
                    history.forEachIndexed { i, s ->
                        val x = i * stepX
                        val y = size.height - (s / maxSpeed * size.height)
                        lineTo(x, y)
                    }
                    lineTo(size.width, size.height)
                    close()
                }

                // 2. 绘制主线条
                val strokePath = Path().apply {
                    history.forEachIndexed { i, s ->
                        val x = i * stepX
                        val y = size.height - (s / maxSpeed * size.height)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
                drawPath(strokePath, color = primaryColor, style = Stroke(width = 4f))
            }
        }
    }
}

@Composable
fun NetworkInterfaceItem(info: NetworkCardInfo, onScanReq: (String) -> Unit) {
    var isPinging by remember { mutableStateOf(false) }
    var pingDisplay by remember { mutableStateOf(info.pingResult) }
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (info.isUp) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (info.isUp) "连接正常" else "已断开",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (info.isUp) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(info.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            info.ipAddresses.forEach { ip ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text("${ip.type}: ${ip.address}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isPinging = true
                            pingDisplay = NetworkTools.pingHost("8.8.8.8")
                            isPinging = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isPinging) {
                        // 修复点：删除 progress 参数，使其调用 Indeterminate 重载版本
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(if (pingDisplay.isEmpty()) "延迟测试" else "Ping: $pingDisplay", fontSize = 12.sp)
                    }
                }

                if (info.ipAddresses.any { it.type == "IPv4" }) {
                    Button(
                        onClick = { onScanReq(info.ipAddresses.first { it.type == "IPv4" }.address) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("内网扫描", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// --- 4. 主 Activity ---

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var networkList by remember { mutableStateOf(emptyList<NetworkCardInfo>()) }
            val speedHistory = remember { mutableStateListOf<Float>() }
            var lanDevices by remember { mutableStateOf(emptyList<String>()) }
            var statusMessage by remember { mutableStateOf("就绪") }
            var ispInfo by remember { mutableStateOf("正在获取运营商信息...") }
            val scope = rememberCoroutineScope()

            // 实时网速监听
            LaunchedEffect(Unit) {
                var lastRx = TrafficStats.getTotalRxBytes()
                while(isActive) {
                    delay(1000)
                    val nowRx = TrafficStats.getTotalRxBytes()
                    speedHistory.add(((nowRx - lastRx) / 1024f).coerceAtLeast(0f))
                    if (speedHistory.size > 30) speedHistory.removeAt(0)
                    lastRx = nowRx
                }
            }

            // 生命周期刷新逻辑
            LifecycleResumeEffect(Unit) {
                networkList = NetworkTools.fetchNetworkInterfaces()
                scope.launch {
                    networkList.firstOrNull()?.ipAddresses?.firstOrNull { it.type == "IPv4" }?.let {
                        val loc = NetworkTools.getIpLocation(it.address)
                        ispInfo = "${loc.isp} · ${loc.city}"
                    }
                }
                onPauseOrDispose {}
            }

            MyApplication1Theme {
                Scaffold(
                    topBar = {
                        Column {
                            CenterAlignedTopAppBar(
                                title = { Text("Network Auditor Pro", fontWeight = FontWeight.Black, fontSize = 20.sp) },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            // 修正点：这里的 LinearProgressIndicator 进度控制
                            if (statusMessage.contains("扫描")) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { networkList = NetworkTools.fetchNetworkInterfaces() },
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) { Icon(Icons.Default.Refresh, "刷新") }
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)
                    ) {
                        item {
                            SpeedChart(speedHistory)

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌍 ISP:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text(ispInfo, fontSize = 12.sp)
                                }
                            }
                        }

                        item {
                            Text("网络接口", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                        }

                        items(networkList) { info ->
                            NetworkInterfaceItem(info) { myIp ->
                                scope.launch {
                                    statusMessage = "正在扫描局域网..."
                                    lanDevices = NetworkTools.scanLocalNetwork(myIp)
                                    statusMessage = "扫描完成"
                                }
                            }
                        }

                        if (lanDevices.isNotEmpty()) {
                            item {
                                Text("发现内网设备 (${lanDevices.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        lanDevices.forEach { ip ->
                                            Text("• $ip", fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}