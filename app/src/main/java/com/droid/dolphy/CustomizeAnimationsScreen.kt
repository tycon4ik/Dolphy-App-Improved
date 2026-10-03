package com.droid.dolphy

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import com.droid.dolphy.util.GifDecoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class ExternalAnimationManifestRow(
    val name: String,
    val minButthurt: Int,
    val maxButthurt: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val weight: Int,
)

data class ExternalAnimationMeta(
    val name: String,
    val root: String = "dolphin/external",
    val minButthurt: Int = 0,
    val maxButthurt: Int = 18,
    val minLevel: Int = 1,
    val maxLevel: Int = 30,
    val weight: Int = 3,
    val frameRate: Int = 2,
    val durationSec: Int = 20,
    val frames: List<String> = emptyList(),
) {
    val isWatchDogs: Boolean get() = root.contains("watchdogs", ignoreCase = true)

    val displayName: String get() {
        return name
            .replace(Regex("""^L\d+_"""), "")
            .replace(Regex("""_\d+x\d+$"""), "")
            .replace('_', ' ')
            .trim()
    }
}

internal var sessionExternalDolphinAnimationName: String? = null

object DolphinAnimationCache {
    private val frameCache = ConcurrentHashMap<String, ImageBitmap>()

    fun getOrDecodeFrame(context: Context, framePath: String): ImageBitmap? {
        frameCache[framePath]?.let { return it }
        return runCatching {
            // Если путь абсолютный (/data/data/...) — читаем из filesDir (кастомная анимация).
            // Иначе — из assets (встроенная анимация).
            val stream = if (framePath.startsWith("/")) {
                java.io.File(framePath).inputStream()
            } else {
                context.assets.open(framePath)
            }
            stream.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()?.also {
                    frameCache[framePath] = it
                }
            }
        }.getOrNull()
    }
}

fun frameNumberFromName(fileName: String): Int {
    return Regex("""frame_(\d+)\.png""")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: Int.MAX_VALUE
}

fun selectWeightedAnimation(items: List<ExternalAnimationMeta>): ExternalAnimationMeta? {
    if (items.isEmpty()) return null
    val totalWeight = items.sumOf { it.weight.coerceAtLeast(1) }
    var lucky = Random.nextInt(totalWeight.coerceAtLeast(1))
    items.forEach { item ->
        lucky -= item.weight.coerceAtLeast(1)
        if (lucky < 0) return item
    }
    return items.last()
}

fun parseManifestRows(raw: String): List<ExternalAnimationManifestRow> {
    val rows = mutableListOf<ExternalAnimationManifestRow>()
    val lines = raw.lineSequence().map { it.trim() }.toList()
    var index = 0
    while (index < lines.size) {
        if (lines[index].startsWith("Name:", true)) {
            val block = mutableMapOf<String, String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                val line = lines[index]
                val sep = line.indexOf(':')
                if (sep > 0) {
                    block[line.substring(0, sep).trim().lowercase()] =
                        line.substring(sep + 1).trim()
                }
                index++
            }
            val name = block["name"] ?: ""
            if (name.isNotBlank()) {
                rows += ExternalAnimationManifestRow(
                    name = name,
                    minButthurt = block["min butthurt"]?.toIntOrNull() ?: 0,
                    maxButthurt = block["max butthurt"]?.toIntOrNull() ?: 18,
                    minLevel = block["min level"]?.toIntOrNull() ?: 1,
                    maxLevel = block["max level"]?.toIntOrNull() ?: 30,
                    weight = block["weight"]?.toIntOrNull() ?: 1,
                )
            }
        }
        index++
    }
    return rows
}

suspend fun loadExternalAnimationMeta(
    context: Context,
    root: String,
): List<ExternalAnimationMeta> {
    val manifestText = runCatching {
        context.assets.open("$root/manifest.txt").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val rows = parseManifestRows(manifestText)
    return rows.mapNotNull { row ->
        val folderPath = "$root/${row.name}"
        val frames = context.assets.list(folderPath)
            ?.filter { it.startsWith("frame_") && it.endsWith(".png") }
            ?.sortedBy { frameNumberFromName(it) }
            ?.map { "$folderPath/$it" }
            .orEmpty()
        if (frames.isEmpty()) return@mapNotNull null
        val metaText = runCatching {
            context.assets.open("$folderPath/meta.txt").bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        val frameRate = Regex("""(?im)^Frame rate:\s*(\d+)""")
            .find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
        val duration = Regex("""(?im)^Duration:\s*(\d+)""")
            .find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 20
        ExternalAnimationMeta(
            name = row.name,
            root = root,
            minButthurt = row.minButthurt,
            maxButthurt = row.maxButthurt,
            minLevel = row.minLevel,
            maxLevel = row.maxLevel,
            weight = row.weight,
            frameRate = frameRate,
            durationSec = duration,
            frames = frames,
        )
    }
}

fun smartAnimationColorFilter(accent: Color, isCustom: Boolean, alpha: Float = 0.58f): ColorFilter {
    // Для кастомных (цветных GIF) приглушаем яркость, чтобы не были светлее стандартных
    val effectiveAlpha = if (isCustom) alpha * 0.72f else alpha
    if (!isCustom) {
        return ColorFilter.tint(
            color = accent.copy(alpha = effectiveAlpha),
            blendMode = BlendMode.Modulate
        )
    }
    val r = accent.red * effectiveAlpha
    val g = accent.green * effectiveAlpha
    val b = accent.blue * effectiveAlpha
    val matrix = floatArrayOf(
        (0.2126f * r), (0.7152f * r), (0.0722f * r), 0f, 0f,
        (0.2126f * g), (0.7152f * g), (0.0722f * g), 0f, 0f,
        (0.2126f * b), (0.7152f * b), (0.0722f * b), 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix))
}

@Composable
fun DolphinAnimationPreview(
    meta: ExternalAnimationMeta,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true
) {
    val context = LocalContext.current
    var frameIndex by remember(meta.name) { mutableIntStateOf(0) }
    val frames = meta.frames

    val frameDelayMs = remember(meta.frameRate) {
        val frameRate = meta.frameRate.coerceAtLeast(1)
        (1000L / (frameRate * 4)).coerceAtLeast(35L)
    }

    LaunchedEffect(meta.name, frames, isPlaying) {
        if (!isPlaying || frames.isEmpty()) return@LaunchedEffect
        frameIndex = 0
        while (true) {
            delay(frameDelayMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val currentFramePath = frames.getOrNull(frameIndex)
    val bitmap = remember(currentFramePath) {
        currentFramePath?.let { DolphinAnimationCache.getOrDecodeFrame(context, it) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val isCustom = currentFramePath?.startsWith("/") == true
            Image(
                bitmap = bitmap,
                contentDescription = meta.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
                contentScale = ContentScale.Fit,
                colorFilter = smartAnimationColorFilter(accent, isCustom, alpha = 0.85f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomizeAnimationsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val prefs = remember(context) {
        context.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
    }

    var pinnedAnimName by remember {
        mutableStateOf(prefs.getString("pinned_dolphin_animation_name", null))
    }

    var reloadTrigger by remember { mutableIntStateOf(0) }

    val allAnimations by androidx.compose.runtime.produceState(
        initialValue = emptyList<ExternalAnimationMeta>(),
        key1 = reloadTrigger
    ) {
        value = withContext(Dispatchers.IO) {
            val external = loadExternalAnimationMeta(context, "dolphin/external")
            val watchdogs = loadExternalAnimationMeta(context, "dolphin/watchdogs")
            val custom = loadCustomAnimationMeta(context)
            external + custom + watchdogs
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val gifPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                importGifToCustomAnimations(context, uri) { success, message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) reloadTrigger++
                }
            }
        }
    }

    var selectedAnimForDialog by remember { mutableStateOf<ExternalAnimationMeta?>(null) }
    var animToDelete by remember { mutableStateOf<ExternalAnimationMeta?>(null) }
    
    if (animToDelete != null) {
        val toDelete = animToDelete!!
        AlertDialog(
            onDismissRequest = { animToDelete = null },
            title = { Text("Удалить анимацию?") },
            text = { Text(toDelete.name) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val dir = File(context.filesDir, "custom_animations/" + toDelete.name)
                        if (dir.exists()) dir.deleteRecursively()
                        animToDelete = null
                        reloadTrigger++
                        android.widget.Toast.makeText(context, "Удалено", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Ошибка: " + (e.message ?: "unknown"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { animToDelete = null }) { Text("Отмена") }
            }
        )
    }

    if (selectedAnimForDialog != null) {
        val anim = selectedAnimForDialog!!
        ExpressiveDialog(
            onDismissRequest = { selectedAnimForDialog = null },
            title = {
                Text(
                    text = stringResource(R.string.customize_animations_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DolphinAnimationPreview(
                        meta = anim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        accent = accentColor
                    )
                    Text(
                        text = anim.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (anim.isWatchDogs) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.18f),
                            contentColor = accentColor
                        ) {
                            Text(
                                text = "WatchDogs",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AccentButton(
                    onClick = {
                        prefs.edit()
                            .putString("pinned_dolphin_animation_name", anim.name)
                            .putString("pinned_dolphin_animation_root", anim.root)
                            .apply()
                        pinnedAnimName = anim.name
                        selectedAnimForDialog = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.customize_animations_set_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.customize_animations_dialog_yes))
                }
            },
            dismissButton = {
                AccentButton(
                    onClick = { selectedAnimForDialog = null }
                ) {
                    Text(stringResource(R.string.customize_animations_dialog_no))
                }
            },
            accentColor = accentColor
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SectionTopBar(
                    title = stringResource(R.string.customize_animations_title),
                    onBack = onBack,
                    actions = {
                        androidx.compose.material3.IconButton(
                            onClick = { gifPicker.launch("image/gif") }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Import GIF",
                                tint = accentColor
                            )
                        }
                        TextButton(
                            onClick = {
                                prefs.edit()
                                    .remove("pinned_dolphin_animation_name")
                                    .remove("pinned_dolphin_animation_root")
                                    .apply()
                                pinnedAnimName = null
                                sessionExternalDolphinAnimationName = null
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.customize_animations_reset_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.customize_animations_reset),
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                )

                if (allAnimations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allAnimations, key = { "${it.root}/${it.name}" }) { anim ->
                            val isPinned = anim.name == pinnedAnimName

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = { selectedAnimForDialog = anim },
                                        onLongClick = {
                                            if (anim.root == "custom") {
                                                animToDelete = anim
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPinned) {
                                        accentColor.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                                border = if (isPinned) {
                                    BorderStroke(2.dp, accentColor)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        DolphinAnimationPreview(
                                            meta = anim,
                                            modifier = Modifier.fillMaxWidth(),
                                            accent = accentColor
                                        )

                                        if (isPinned) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor,
                                                contentColor = Color.White
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Star,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.customize_animations_permanent_badge),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = anim.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPinned) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPinned) accentColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (anim.isWatchDogs) {
                                                accentColor.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (anim.isWatchDogs) {
                                                accentColor
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = if (anim.isWatchDogs) "WatchDogs" else "Classic",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
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


suspend fun loadCustomAnimationMeta(context: Context): List<ExternalAnimationMeta> {
    return withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "custom_animations")
        if (!baseDir.exists()) return@withContext emptyList()

        val dirs = baseDir.listFiles()?.filter { it.isDirectory } ?: return@withContext emptyList()

        dirs.mapNotNull { dir ->
            val frames = dir.listFiles()
                ?.filter { it.name.startsWith("frame_") && it.name.endsWith(".png") }
                ?.sortedBy { frameNumberFromName(it.name) }
                ?.map { it.absolutePath }
                .orEmpty()
            if (frames.isEmpty()) return@mapNotNull null

            val metaText = runCatching {
                File(dir, "meta.txt").readText()
            }.getOrNull().orEmpty()

            val frameRateRegex = Regex("(?im)^Frame rate:\\s*(\\d+)")
            val durationRegex = Regex("(?im)^Duration:\\s*(\\d+)")
            val frameRate = frameRateRegex.find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
            val duration = durationRegex.find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 20

            ExternalAnimationMeta(
                name = dir.name,
                root = "custom",
                minButthurt = 0,
                maxButthurt = 18,
                minLevel = 1,
                maxLevel = 999,
                weight = 1,
                frameRate = frameRate,
                durationSec = duration,
                frames = frames,
            )
        }
    }
}

suspend fun importGifToCustomAnimations(
    context: Context,
    uri: android.net.Uri,
    onResult: (Boolean, String) -> Unit,
) {
    try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            onResult(false, "Не удалось открыть файл")
            return
        }

        val decoder = GifDecoder()
        val code = withContext(Dispatchers.IO) { decoder.read(input) }
        input.close()

        if (code != 0 || decoder.frameCount == 0) {
            onResult(false, "Ошибка декодирования GIF")
            return
        }

        val name = "gif_" + System.currentTimeMillis()
        val dir = File(context.filesDir, "custom_animations/" + name)
        if (!dir.exists() && !dir.mkdirs()) {
            onResult(false, "Не удалось создать папку")
            return
        }

        withContext(Dispatchers.IO) {
            for (i in 0 until decoder.frameCount) {
                val bmp = decoder.getFrame(i) ?: continue
                // Вариант A: перекрашиваем кадр в белый на прозрачном фоне.
                // Тогда ColorFilter.tint(accent, Modulate) даст правильный accent-цвет,
                // как у стандартных анимаций.
                val bw = android.graphics.Bitmap.createBitmap(
                    bmp.width, bmp.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                for (j in pixels.indices) {
                    val px = pixels[j]
                    val a = (px ushr 24) and 0xFF
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                    pixels[j] = if (lum > 96 && a > 32) {
                        0xFFFFFFFF.toInt()  // белый
                    } else {
                        0x00000000           // прозрачный
                    }
                }
                bw.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val file = File(dir, "frame_" + i + ".png")
                file.outputStream().use { out ->
                    bw.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                bw.recycle()
            }

            var totalDelay = 0
            for (i in 0 until decoder.frameCount) totalDelay += decoder.getDelay(i)
            val avgDelay = if (decoder.frameCount > 0) totalDelay / decoder.frameCount else 100
            val frameRate = if (avgDelay > 0) (1000 / avgDelay).coerceIn(1, 30) else 10

            val meta = "Frame rate: " + frameRate + "\\nDuration: 3600\\n"
            File(dir, "meta.txt").writeText(meta)
        }

        onResult(true, "Загружено " + decoder.frameCount + " кадров")
    } catch (e: Exception) {
        onResult(false, "Ошибка: " + (e.message ?: "unknown"))
    }
}
