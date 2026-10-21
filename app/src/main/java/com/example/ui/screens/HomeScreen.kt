package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.viewmodel.DS4ViewModel

/**
 * HomeScreen forwards to [DashboardScreen], maintaining backwards compatibility.
 */
@Composable
fun HomeScreen(
    viewModel: DS4ViewModel,
    onNavigateToLightBar: () -> Unit,
    onNavigateToTester: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardScreen(
        viewModel = viewModel,
        onNavigateToLightBar = onNavigateToLightBar,
        onNavigateToTester = onNavigateToTester,
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        modifier = modifier
    )
}
