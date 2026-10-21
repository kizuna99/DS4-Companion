package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoute(val route: String) {
    object Home : NavRoute("home")
    object LightBar : NavRoute("light_bar")
    object InputTest : NavRoute("input_test")
    object Diagnostics : NavRoute("diagnostics")
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(
        route = NavRoute.Home.route,
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    BottomNavItem(
        route = NavRoute.LightBar.route,
        title = "Light Bar",
        selectedIcon = Icons.Filled.Palette,
        unselectedIcon = Icons.Outlined.Palette,
        testTag = "nav_lightbar"
    ),
    BottomNavItem(
        route = NavRoute.InputTest.route,
        title = "Input Test",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_tester"
    ),
    BottomNavItem(
        route = NavRoute.Diagnostics.route,
        title = "Diagnostics",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_diagnostics"
    )
)
