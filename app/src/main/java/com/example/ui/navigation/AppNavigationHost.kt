package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.InputTesterScreen
import com.example.ui.screens.LightBarScreen
import com.example.viewmodel.DS4ViewModel

/**
 * NavigationHost using NavController to navigate between Home, Light Bar, Input Test, and Diagnostics.
 */
@Composable
fun AppNavigationHost(
    navController: NavHostController,
    viewModel: DS4ViewModel,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.Home.route,
        modifier = modifier
    ) {
        composable(NavRoute.Home.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToLightBar = {
                    navController.navigate(NavRoute.LightBar.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToTester = {
                    navController.navigate(NavRoute.InputTest.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenBluetoothSettings = onOpenBluetoothSettings
            )
        }

        composable(NavRoute.LightBar.route) {
            BackHandler {
                navController.popBackStack(NavRoute.Home.route, inclusive = false)
            }
            LightBarScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack(NavRoute.Home.route, inclusive = false)
                }
            )
        }

        composable(NavRoute.InputTest.route) {
            BackHandler {
                navController.popBackStack(NavRoute.Home.route, inclusive = false)
            }
            InputTesterScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack(NavRoute.Home.route, inclusive = false)
                }
            )
        }

        composable(NavRoute.Diagnostics.route) {
            BackHandler {
                navController.popBackStack(NavRoute.Home.route, inclusive = false)
            }
            DiagnosticsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack(NavRoute.Home.route, inclusive = false)
                }
            )
        }
    }
}
