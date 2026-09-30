package com.droid.dolphy

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import rikka.shizuku.Shizuku

/**
 * Экран «Root Tools» — определяет модель/ядро/прошивку,
 * проверяет статус Shizuku, сверяет с базой Root My Galaxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootToolsScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val context = androidx.compose.ui.platform.LocalContext.current

    var kernelVersion by remember { mutableStateOf("—") }
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuGranted by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logs = (logs + "$time  $msg").takeLast(30)
    }

    fun refreshShizuku() {
        shizukuRunning = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        shizukuGranted = try {
            shizukuRunning && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    LaunchedEffect(Unit) {
        addLog("Root Tools loaded")
        refreshShizuku()
        addLog("Shizuku running: $shizukuRunning, granted: $shizukuGranted")
        // kernel version через Shizuku (uname -r) или System property
        try {
            if (shizukuGranted) {
                val result = ShizukuHelper.runShellCommandWithOutput("uname -r")
                val json = org.json.JSONObject(result)
                val out = json.optString("out", "").trim()
                if (out.isNotEmpty()) {
                    kernelVersion = out
                    addLog("kernel: $out")
                } else {
                    addLog("uname empty, err: ${json.optString("err", "")}")
                }
            } else {
                val fallback = System.getProperty("os.version") ?: "—"
                kernelVersion = fallback
                addLog("kernel (fallback): $fallback")
            }
        } catch (e: Throwable) {
            addLog("kernel error: ${e.message}")
        }
    }

    fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                addLog("Shizuku not running")
                return
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
                addLog("Permission requested")
            } else {
                addLog("Already granted")
            }
        } catch (e: Throwable) {
            addLog("request error: ${e.message}")
        }
        refreshShizuku()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root Tools") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Устройство", style = MaterialTheme.typography.titleMedium, color = accent)
                        InfoRow("Модель", Build.MODEL)
                        InfoRow("Производитель", Build.MANUFACTURER)
                        InfoRow("Прошивка", Build.DISPLAY)
                        InfoRow("Ядро", kernelVersion)
                        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Shizuku", style = MaterialTheme.typography.titleMedium, color = accent)
                        InfoRow("Binder активен", if (shizukuRunning) "✓ Да" else "✗ Нет")
                        InfoRow("Разрешение", if (shizukuGranted) "✓ Есть" else "✗ Нет")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { refreshShizuku(); addLog("refreshed") }) {
                                Text("Обновить")
                            }
                            if (shizukuRunning && !shizukuGranted) {
                                OutlinedButton(onClick = { requestShizuku() }) {
                                    Text("Запросить доступ")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("База payloads", style = MaterialTheme.typography.titleMedium, color = accent)
                        Text(
                            "Сверка с Root-My-Galaxy-Payloads по модели и ядру. " +
                            "Если совпадение найдено — можно скачать ELF и запустить через Shizuku.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { addLog("TODO: download targets-v3.json") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = shizukuGranted
                        ) {
                            Text("Проверить поддержку")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Логи", style = MaterialTheme.typography.labelMedium, color = accent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            logs.joinToString("\n").ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF80FF80)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
