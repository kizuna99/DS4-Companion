package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionStatus
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InputTesterScreen
import com.example.ui.screens.LightBarScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import android.view.PointerIcon
import com.example.viewmodel.DS4ViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DS4ViewModel by viewModels {
        DS4ViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.controllerManager.bindLifecycle(lifecycle)

        setContent {
            MyApplicationTheme {
                MainAppScaffold(
                    viewModel = viewModel,
                    onOpenBluetoothSettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        suppressTouchpadCursor(true)
    }

    override fun onPause() {
        super.onPause()
        suppressTouchpadCursor(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        suppressTouchpadCursor(hasFocus)
    }

    private fun suppressTouchpadCursor(suppress: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val iconType = if (suppress) PointerIcon.TYPE_NULL else PointerIcon.TYPE_DEFAULT
            window.decorView.pointerIcon = PointerIcon.getSystemIcon(this, iconType)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Dispatches controller key presses to input repository
        if (viewModel.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // If this motion event is from the DS4 physical touchpad / pointer,
        // process it for the touchpad test and consume it so it does not trigger
        // hover highlights or scroll behavior across the app UI.
        if (viewModel.isDs4PointerEvent(event)) {
            viewModel.handleMotionEvent(event)
            return true
        }

        // Dispatches controller analog sticks and triggers to input repository
        if (viewModel.handleMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 1. Phone touchscreen touches must ALWAYS route directly to the UI and
        //    must NEVER be delivered to the DS4 touchpad state!
        val isTouchscreen = event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN) ||
                ((event.device?.sources ?: 0) and android.view.InputDevice.SOURCE_TOUCHSCREEN) != 0

        if (isTouchscreen) {
            return super.dispatchTouchEvent(event)
        }

        // 2. Genuine DS4 physical touchpad events (clicks / contact taps):
        //    Route to DS4 input repository and consume the event so that
        //    touchpad presses/clicks do not trigger unintended UI buttons in DS4 Companion.
        if (viewModel.isDs4PointerEvent(event)) {
            viewModel.handleTouchEvent(event)
            return true
        }

        // 3. Ordinary external mice or non-DS4 pointers interact with the UI normally.
        return super.dispatchTouchEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: DS4ViewModel,
    onOpenBluetoothSettings: () -> Unit
) {
    val navController = androidx.navigation.compose.rememberNavController()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    // Request Bluetooth permissions on Android 12+ if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DS4 Companion",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val statusDotColor = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> StatusSuccess
                            ConnectionStatus.CONNECTING -> StatusWarning
                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            com.example.ui.navigation.AppBottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        com.example.ui.navigation.AppNavigationHost(
            navController = navController,
            viewModel = viewModel,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
