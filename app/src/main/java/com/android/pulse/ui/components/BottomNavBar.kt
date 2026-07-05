package com.android.pulse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : NavItem("home", Icons.Default.Home, "Home")
    object Explore : NavItem("explore", Icons.Default.Explore, "Explore")
    object Library : NavItem("library", Icons.Default.LibraryMusic, "Library")
    object Settings : NavItem("settings", Icons.Default.Settings, "Settings")
    object LikedSongs : NavItem("liked_songs", Icons.Default.Favorite, "Liked")
    object Downloads : NavItem("downloads", Icons.Default.Download, "Downloads")
}

@Composable
fun BottomNavBar(selectedRoute: String, onItemClick: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        val items = listOf(NavItem.Home, NavItem.Explore, NavItem.Library, NavItem.Settings)
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = selectedRoute == item.route,
                onClick = { onItemClick(item.route) }
            )
        }
    }
}
