package com.droid.dolphy.plugin.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginRegistryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class CatalogPlugin(
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val icon: String,
    val downloadUrl: String,
    val registry: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginCatalogScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary

    var plugins by remember { mutableStateOf<List<CatalogPlugin>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var installingUrl by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<PendingInstall?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            errorText = null
            try {
                val registries = PluginRegistryStore.list(context)
                val all = mutableListOf<CatalogPlugin>()
                for (reg in registries) {
                    val list = withContext(Dispatchers.IO) { fetchRegistry(reg) }
                    all.addAll(list)
                }
                plugins = all
            } catch (e: Exception) {
                errorText = e.message ?: "Ошибка"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог плагинов") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading && plugins.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor
                )
                errorText != null && plugins.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ошибка: " + errorText, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refresh() }) { Text("Повторить") }
                }
                plugins.isEmpty() -> Text(
                    "Нет плагинов. Добавьте реестр через + на странице плагинов.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(plugins, key = { it.downloadUrl }) { plugin ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(plugin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("v" + plugin.version + " · " + plugin.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (installingUrl == plugin.downloadUrl) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor)
                                    } else {
                                        Button(onClick = {
                                            scope.launch {
                                                installingUrl = plugin.downloadUrl
                                                val pending = withContext(Dispatchers.IO) { downloadForPreview(context, plugin) }
                                                installingUrl = null
                                                if (pending == null) {
                                                    android.widget.Toast.makeText(context, "Ошибка скачивания", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    pendingPreview = pending
                                                }
                                            }
                                        }) { Text("Установить") }
                                    }
                                }
                                if (plugin.description.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(plugin.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    val preview = pendingPreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { pendingPreview = null },
            title = {
                Text(preview.plugin.name + " " + preview.plugin.version)
            },
            text = {
                Column {
                    Text("Автор: " + preview.plugin.author, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    if (preview.plugin.description.isNotBlank()) {
                        Text(preview.plugin.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Размер: " + (preview.sizeBytes / 1024) + " КБ", style = MaterialTheme.typography.bodySmall)
                    if (preview.capabilities.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Разрешения:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        preview.capabilities.forEach { cap ->
                            Text("• " + cap, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { installPluginFromFile(preview.tmpFile) }
                        pendingPreview = null
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "Установлено" else "Ошибка установки",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        if (ok) refresh()
                    }
                }) { Text("Установить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview.tmpFile.delete()
                    pendingPreview = null
                }) { Text("Отмена") }
            }
        )
    }
}

private fun fetchRegistry(url: String): List<CatalogPlugin> {
    val result = mutableListOf<CatalogPlugin>()
    try {
        val fileUrls = resolveFileUrls(url)
        val seen = mutableSetOf<String>()
        for (fileUrl in fileUrls) {
            val head = httpGetFirstBytes(fileUrl, 4096) ?: continue
            val meta = parseMeta(head) ?: continue
            val dupKey = (meta["name"] ?: "?") + "@" + (meta["version"] ?: "?")
            if (seen.contains(dupKey)) continue
            seen.add(dupKey)
            result.add(CatalogPlugin(
                name = meta["name"] ?: "Unknown",
                description = meta["description"] ?: "",
                author = meta["author"] ?: "",
                version = meta["version"] ?: "?",
                icon = meta["icon"] ?: "extension",
                downloadUrl = fileUrl,
                registry = url
            ))
        }
    } catch (_: Exception) {}
    return result
}

private fun resolveFileUrls(url: String): List<String> {
    val trimmed = url.trimEnd().removeSuffix("/")
    val GH = "github.com/"
    if (!trimmed.contains(GH)) {
        // Не GitHub — просто HTML
        val html = httpGet(trimmed) ?: return emptyList()
        return extractDolphyHrefs(html, trimmed)
    }
    // GitHub
    val afterHost = trimmed.substring(trimmed.indexOf(GH) + GH.length)
    val parts = afterHost.split("/").filter { it.isNotBlank() }
    if (parts.size < 2) return emptyList()
    val user = parts[0]
    val repo = parts[1].removeSuffix(".git")

    // 1. GitHub API
    val apiUrl = "https://api.github.com/repos/" + user + "/" + repo + "/contents/"
    val json = httpGet(apiUrl)
    val badApi = json == null || json.contains("message")
    if (!badApi && json != null) {
        val parsed = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i)
                if (obj == null) null
                else {
                    val name = obj.optString("name", "")
                    if (name.endsWith(".dolphyplugin")) obj.optString("download_url", null) else null
                }
            }
        }.getOrNull()
        if (!parsed.isNullOrEmpty()) return parsed
    }

    // 2. HTML страница репозитория
    val html = httpGet(trimmed)
    if (html != null) {
        val marker = "/" + user + "/" + repo + "/blob/"
        val names = mutableListOf<String>()
        var idx = html.indexOf(marker)
        while (idx >= 0) {
            val afterMarker = html.substring(idx + marker.length)
            val closeQuote = afterMarker.indexOf("\"")
            if (closeQuote > 0) {
                val fullPath = afterMarker.substring(0, closeQuote)
                val fileName = fullPath.substringAfterLast("/")
                if (fileName.endsWith(".dolphyplugin") && !names.contains(fileName)) {
                    names.add(fileName)
                }
            }
            idx = html.indexOf(marker, idx + marker.length)
        }
        if (names.isNotEmpty()) {
            val urls = mutableListOf<String>()
            for (fileName in names) {
                urls.add("https://raw.githubusercontent.com/" + user + "/" + repo + "/main/" + fileName)

            }
            return urls
        }
    }

    // 3. Fallback — text.dolphyplugin по обоим веткам
    return listOf(
        "https://raw.githubusercontent.com/" + user + "/" + repo + "/main/text.dolphyplugin",
        
    )
}

private fun extractDolphyHrefs(html: String, baseUrl: String): List<String> {
    val result = mutableListOf<String>()
    val needles = listOf("href=\"", "href=\u0027")
    for (needle in needles) {
        var idx = html.indexOf(needle)
        while (idx >= 0) {
            val after = html.substring(idx + needle.length)
            val closeChar = if (needle.endsWith("\"")) "\"" else "\u0027"
            val close = after.indexOf(closeChar)
            if (close > 0) {
                val href = after.substring(0, close)
                if (href.contains(".dolphyplugin")) {
                    val full = when {
                        href.startsWith("http") -> href
                        href.startsWith("/") -> baseUrl + href
                        else -> baseUrl + "/" + href
                    }
                    if (!result.contains(full)) result.add(full)
                }
            }
            idx = html.indexOf(needle, idx + needle.length)
        }
    }
    return result
}
private fun parseMeta(head: String): Map<String, String>? {
    val regex = Regex("^\\s*__(\\w+)__\\s*=\\s*([\"\u0027])([^\"\u0027]+)\\2", RegexOption.MULTILINE)
    val result = mutableMapOf<String, String>()
    regex.findAll(head).forEach { m ->
        val key = m.groupValues[1]
        val value = m.groupValues[3]
        result[key] = value
    }
    val name = result["name"] ?: return null
    return mapOf(
        "name" to name,
        "description" to (result["description"] ?: ""),
        "author" to (result["author"] ?: ""),
        "version" to (result["version"] ?: "?"),
        "icon" to (result["icon"] ?: "extension")
    )
}

private fun httpGet(url: String): String? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Dolphy")
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }
}

private fun httpGetFirstBytes(url: String, max: Int): String? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Dolphy")
        val buf = ByteArray(max)
        val read = conn.inputStream.use { it.read(buf) }
        if (read <= 0) null else String(buf, 0, read, Charsets.UTF_8)
    } catch (_: Exception) { null }
}

private fun installPlugin(context: Context, url: String): Boolean {
    return try {
        val tmp = File(context.cacheDir, "dl_" + System.currentTimeMillis() + ".dolphyplugin")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Dolphy")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        val result = PluginManager.installFromFile(tmp)
        tmp.delete()
        result.isSuccess
    } catch (_: Exception) { false }
}


data class PendingInstall(
    val plugin: CatalogPlugin,
    val tmpFile: File,
    val sizeBytes: Long,
    val capabilities: List<String>,
)

private fun downloadForPreview(context: Context, plugin: CatalogPlugin): PendingInstall? {
    return try {
        val tmp = File(context.cacheDir, "preview_" + System.currentTimeMillis() + ".dolphyplugin")
        val conn = URL(plugin.downloadUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Dolphy")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        val previewResult = PluginManager.previewFromUri(android.net.Uri.fromFile(tmp))
        val preview = previewResult.getOrNull()
        if (preview == null) {
            tmp.delete()
            return null
        }
        PendingInstall(
            plugin = plugin,
            tmpFile = tmp,
            sizeBytes = preview.sizeBytes,
            capabilities = preview.capabilities,
        )
    } catch (_: Exception) {
        null
    }
}

private fun installPluginFromFile(file: File): Boolean {
    return try {
        val result = PluginManager.installFromUri(android.net.Uri.fromFile(file))
        file.delete()
        result.isSuccess
    } catch (_: Exception) {
        false
    }
}