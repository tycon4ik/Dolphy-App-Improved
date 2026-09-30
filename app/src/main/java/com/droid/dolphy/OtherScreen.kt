package com.droid.dolphy

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Security
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(navController: NavController, spamViewModel: SpamViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val sections = functionDestinationSections()

    MaterialBackground(accentColor = accent) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
            ) {
                item(key = "root_tools_card") {
                    RootToolsEntryCard(
                        accent = accent,
                        navController = navController,
                    )
                }
                sections.forEach { (section, destinations) ->
                    item(key = section) {
                        FunctionSectionBlock(
                            title = section,
                            items = destinations,
                            accent = accent,
                            onClick = { destination ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                openFunctionDestination(destination, navController, context)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionSectionBlock(
    title: String,
    items: List<FunctionDestination>,
    accent: androidx.compose.ui.graphics.Color,
    onClick: (FunctionDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        M3SegmentedListSectionHeader(title = title)
        M3SegmentedList(items = items) { index, count, item ->
            M3SegmentedListItem(
                index = index,
                count = count,
                headline = item.title,
                supporting = item.description.takeIf { it.isNotBlank() },
                leadingIcon = item.icon,
                showChevron = !item.requiresRoot,
                trailingContent = if (item.requiresRoot) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RootBadge(accentColor = accent)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else null,
                onClick = { onClick(item) },
            )
        }
    }
}


@Composable
private fun RootToolsEntryCard(
    accent: androidx.compose.ui.graphics.Color,
    navController: NavController,
) {
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shizukuRunning = try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
        shizukuGranted = try {
            shizukuRunning && rikka.shizuku.Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    val statusLine = buildString {
        append(android.os.Build.MODEL)
        append("  ·  ")
        append(System.getProperty("os.version") ?: "—")
        append("  ·  ")
        append(
            when {
                shizukuGranted -> "Shizuku ✓"
                shizukuRunning -> "Shizuku (нет доступа)"
                else -> "Shizuku ✗"
            }
        )
    }

    MaterialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("root_tools") },
        accentColor = accent,
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Root Tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
