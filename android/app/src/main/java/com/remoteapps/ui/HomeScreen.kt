package com.remoteapps.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteapps.core.security.PreferenceManager
import com.remoteapps.host.service.HostAccessibilityService
import com.remoteapps.host.service.HostForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: PreferenceManager,
    isHostServiceActive: Boolean,
    onStartHostService: () -> Unit,
    onStopHostService: () -> Unit,
    onConnectToTarget: (targetId: String, pin: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("RemoteApps", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Phone & PC Remote Control", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Beri Akses (Host)", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Kendalikan (Remote)", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    HostTabContent(
                        prefs = prefs,
                        isHostServiceActive = isHostServiceActive,
                        onStartHost = onStartHostService,
                        onStopHost = onStopHostService
                    )
                } else {
                    ControllerTabContent(
                        prefs = prefs,
                        onConnect = onConnectToTarget
                    )
                }
            }
        }
    }
}

@Composable
fun HostTabContent(
    prefs: PreferenceManager,
    isHostServiceActive: Boolean,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit
) {
    val context = LocalContext.current
    var pinValue by remember { mutableStateOf(prefs.unattendedPin) }
    var serverUrlValue by remember { mutableStateOf(prefs.serverUrl) }
    val isAccessibilityActive = HostAccessibilityService.isServiceRunning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device ID Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ID PERANGKAT INI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prefs.deviceId,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", prefs.deviceId))
                        Toast.makeText(context, "ID berhasil disalin!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin ID", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "Gunakan ID ini untuk meremote HP ini dari HP lain atau dari PC browser.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // Accessibility Service Status
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Izin Sentuhan (Aksesibilitas)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAccessibilityActive) "Aktif (Sentuhan & Gestur siap)" else "Belum aktif (Wajib diaktifkan 1x)",
                            fontSize = 12.sp,
                            color = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                    if (!isAccessibilityActive) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Buka Pengaturan", fontSize = 12.sp)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    }
                }
            }
        }

        // Unattended PIN Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kunci Unattended Access (PIN)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "Pengendali yang memasukkan PIN ini akan otomatis terhubung tanpa Anda perlu menekan tombol konfirmasi di HP ini.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = {
                        if (it.length <= 8) {
                            pinValue = it
                            prefs.unattendedPin = it
                        }
                    },
                    label = { Text("PIN Akses Tetap") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Service Control Switch & Action
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isHostServiceActive) Color(0xFF143026) else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Status Layanan Remote", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (isHostServiceActive) "Layanan Berjalan di Latar Belakang" else "Layanan Mati",
                            fontSize = 12.sp,
                            color = if (isHostServiceActive) Color(0xFF10B981) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isHostServiceActive,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!isAccessibilityActive) {
                                    Toast.makeText(context, "Harap aktifkan Izin Aksesibilitas terlebih dahulu!", Toast.LENGTH_LONG).show()
                                } else {
                                    onStartHost()
                                }
                            } else {
                                onStopHost()
                            }
                        }
                    )
                }

                if (isHostServiceActive) {
                    Text(
                        text = "🟢 HP ini siap diremote kapan saja dari HP lain atau PC browser tanpa konfirmasi ulang.",
                        fontSize = 12.sp,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }
        }

        // Server URL Configuration
        OutlinedTextField(
            value = serverUrlValue,
            onValueChange = {
                serverUrlValue = it
                prefs.serverUrl = it
            },
            label = { Text("Signaling Server URL") },
            placeholder = { Text("ws://192.168.1.100:3000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ControllerTabContent(
    prefs: PreferenceManager,
    onConnect: (targetId: String, pin: String) -> Unit
) {
    val context = LocalContext.current
    var targetIdInput by remember { mutableStateOf(prefs.lastTargetId) }
    var pinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Hubungkan ke HP Target", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = "Masukkan ID perangkat HP yang ingin Anda kendalikan dan PIN Unattended-nya.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = targetIdInput,
                    onValueChange = { targetIdInput = it },
                    label = { Text("Device ID Target (XXX-XXX-XXX)") },
                    placeholder = { Text("Contoh: 123-456-789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("PIN Akses") },
                    placeholder = { Text("PIN 6-digit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val cleanId = targetIdInput.trim()
                        val cleanPin = pinInput.trim()
                        if (cleanId.isEmpty() || cleanPin.isEmpty()) {
                            Toast.makeText(context, "ID dan PIN tidak boleh kosong", Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.lastTargetId = cleanId
                            onConnect(cleanId, cleanPin)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hubungkan Sekarang", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // PC Browser Remote Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💻", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mau remote dari PC / Laptop?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = "Anda juga bisa membuka browser di laptop/PC ke alamat signaling server (${prefs.serverUrl.replace("ws://", "http://")}) untuk mengendalikan HP ini dengan mouse & keyboard tanpa perlu instal aplikasi apapun di PC!",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
