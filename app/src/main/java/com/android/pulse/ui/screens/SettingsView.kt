package com.android.pulse.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.pulse.audio.AudioPlayerManager
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    audioPlayerManager: AudioPlayerManager, 
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
            ) {
                Text(
                    "Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Playback & Audio Category
                SettingsSection("Playback & Audio") {
                    var audioQuality by remember { mutableStateOf("Normal") }
                    SettingsListRow(
                        icon = Icons.Default.HighQuality,
                        title = "Audio Quality",
                        subtitle = audioQuality,
                        onClick = { /* Show selection dialog */ }
                    )
                    
                    var crossfade by remember { mutableFloatStateOf(0f) }
                    SettingsSliderRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Crossfade",
                        value = crossfade,
                        onValueChange = { crossfade = it },
                        valueRange = 0f..12f,
                        unit = "s"
                    )
                }

                // Storage Category
                SettingsSection("Storage") {
                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "Clear History",
                        subtitle = "Remove recently played songs",
                        onClick = {
                            scope.launch {
                                audioPlayerManager.database.historyDao().clearHistory()
                                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    
                    var showDestructiveDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.History,
                        title = "Clear Database",
                        subtitle = "Reset all app data",
                        onClick = { showDestructiveDialog = true },
                        isDestructive = true
                    )
                    
                    if (showDestructiveDialog) {
                        AlertDialog(
                            onDismissRequest = { showDestructiveDialog = false },
                            title = { Text("Reset app data?") },
                            text = { Text("This will permanently delete your playlists and liked songs.") },
                            confirmButton = {
                                TextButton(
                                    onClick = { 
                                        /* Global reset logic */
                                        showDestructiveDialog = false 
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Reset Everything") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDestructiveDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }

                // App Interface Category
                SettingsSection("Interface") {
                    var dynamicColor by remember { mutableStateOf(true) }
                    SettingsSwitchRow(
                        icon = Icons.Default.Palette,
                        title = "Dynamic Colors",
                        subtitle = "Use wallpaper colors (Monet)",
                        checked = dynamicColor,
                        onCheckedChange = { dynamicColor = it }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About Pulse",
                        subtitle = "Developer info and support",
                        onClick = onAboutClick
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector, 
    title: String, 
    subtitle: String, 
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SettingsSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSliderRow(icon: ImageVector, title: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, unit: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            Text("${value.toInt()}$unit", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun SettingsListRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
