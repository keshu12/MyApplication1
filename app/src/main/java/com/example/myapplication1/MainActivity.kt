package com.example.myapplication1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.myapplication1.ui.theme.MyApplication1Theme
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

// --- 1. 数据模型 ---

data class IpAddressInfo(
    val type: String,
    val address: String
)

data class NetworkCardInfo(
    val displayName: String,
    val name: String,
    val isUp: Boolean,
    val macAddress: String,
    val ipAddresses: List<IpAddressInfo>
)

// --- 2. 逻辑处理 ---

fun fetchNetworkInterfaces(): List<NetworkCardInfo> {
    return try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        interfaces.map { intf ->
            val ipInfos = Collections.list(intf.inetAddresses)
                .filter { !it.isLoopbackAddress }
                .map { addr ->
                    IpAddressInfo(
                        type = if (addr is Inet6Address) "IPv6" else "IPv4",
                        address = addr.hostAddress?.split('%')?.get(0) ?: "Unknown"
                    )
                }

            val mac = try {
                intf.hardwareAddress?.joinToString(":") { "%02X".format(it) } ?: "N/A"
            } catch (e: Exception) {
                "Error"
            }

            NetworkCardInfo(
                displayName = intf.displayName,
                name = intf.name,
                isUp = intf.isUp,
                macAddress = mac,
                ipAddresses = ipInfos
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// --- 3. UI 界面组件 ---

@Composable
fun NetworkInterfaceItem(info: NetworkCardInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部：接口名和状态标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = if (info.isUp) Color(0xFF4CAF50) else Color.Gray,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (info.isUp) "UP" else "DOWN",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 详细信息
            InfoRow(label = "Internal Name", value = info.name)
            InfoRow(label = "MAC Address", value = info.macAddress, isMonospace = true)

            if (info.ipAddresses.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                info.ipAddresses.forEach { ip ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "${ip.type}: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = ip.address,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isMonospace) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// --- 4. 主 Activity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var networkList by remember { mutableStateOf(emptyList<NetworkCardInfo>()) }
            var isRefreshing by remember { mutableStateOf(false) }

            // 自动刷新逻辑
            LifecycleResumeEffect(Unit) {
                networkList = fetchNetworkInterfaces()
                onPauseOrDispose { }
            }

            MyApplication1Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(title = { Text("Network Auditor") })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            networkList = fetchNetworkInterfaces()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (networkList.isEmpty()) {
                            item {
                                Text("No active interfaces found.", modifier = Modifier.padding(16.dp))
                            }
                        }
                        items(networkList) { info ->
                            NetworkInterfaceItem(info)
                        }
                    }
                }
            }
        }
    }
}