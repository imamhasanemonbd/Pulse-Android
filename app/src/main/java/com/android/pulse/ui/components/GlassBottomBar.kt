package com.android.pulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.pulse.ui.theme.PrimaryPink

@Composable
fun GlassBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFF0F0F14).copy(alpha = 0.45f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            label = "Listen Now",
            icon = Icons.Default.Home,
            selected = currentRoute == "home",
            onClick = { onNavigate("home") }
        )
        BottomNavItem(
            label = "Explore",
            icon = Icons.Default.Search, // Using Search for Explore as placeholder
            selected = currentRoute == "explore",
            onClick = { onNavigate("explore") }
        )
        BottomNavItem(
            label = "Library",
            icon = Icons.Default.LibraryMusic,
            selected = currentRoute == "library",
            onClick = { onNavigate("library") }
        )
        BottomNavItem(
            label = "Settings",
            icon = Icons.Default.Settings,
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") }
        )
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) PrimaryPink else Color.Gray
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp
        )
    }
}
