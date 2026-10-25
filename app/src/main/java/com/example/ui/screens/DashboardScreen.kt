package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.ConnectionStatus
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.model.TransportType
import com.example.ui.components.DS4ControllerVector
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.DS4ViewModel

/**
 * Main Dashboard UI built using Jetpack Compose and Material 3.
 *
 * Incorporates:
 * 1. Connection status indicator badge with live state colors.
 * 2. Central illustration placeholder showcasing the Sony DualShock 4 controller.
 * 3. Dedicated placeholders/cards for battery percentage and connection type.
 * 4. Fast action controls (Connect, Disconnect, Bluetooth shortcut, Simulation toggle).
 */
@Composable
fun DashboardScreen(
    viewModel: DS4ViewModel,
    onNavigateToLightBar: () -> Unit,
    onNavigateToTester: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val currentDevice by viewModel.currentDevice.collectAsStateWithLifecycle()
    val lightBarColor by viewModel.lightBarColor.collectAsStateWithLifecycle()
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    val isSimulation by viewModel.isSimulationMode.collectAsStateWithLifecycle()
    val batteryInfo by viewModel.batteryState.collectAsStateWithLifecycle()
    val batteryHistory by viewModel.batteryHistory.collectAsStateWithLifecycle()
    val batteryDrainStats by viewModel.batteryDrainStats.collectAsStateWithLifecycle()

    var showPairingSteps by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Top Header: Status Indicator & Quick Scan ---
        item {
            DashboardHeader(
                status = connectionStatus,
                onRefresh = { viewModel.refresh() }
            )
        }

        // --- 2. Central Controller Illustration Card ---
        item {
            CentralControllerIllustrationCard(
                lightBarColor = lightBarColor,
                inputState = inputState,
                device = currentDevice,
                onNavigateToLightBar = onNavigateToLightBar
            )
        }

        // --- 3. Telemetry Row: Battery Percentage & Connection Type Placeholders ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery Percentage Placeholder
                BatteryPlaceholderCard(
                    batteryInfo = batteryInfo,
                    modifier = Modifier.weight(1f)
                )

                // Connection Type Placeholder
                ConnectionTypePlaceholderCard(
                    device = currentDevice,
                    connectionStatus = connectionStatus,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 4. Real-Time Battery Drainage Over Time Line Chart ---
        item {
            com.example.ui.components.BatteryDrainageChartCard(
                batteryInfo = batteryInfo,
                batteryHistory = batteryHistory,
                drainStats = batteryDrainStats,
                connectionStatus = connectionStatus,
                isSimulationMode = isSimulation,
                onSimulateDrainStep = { viewModel.simulateBatteryDrainStep(2) },
                onResetHistory = { viewModel.clearBatteryHistory() }
            )
        }

        // --- 5. Main Connection Control & Action Bar ---
        item {
            DashboardActionCard(
                status = connectionStatus,
                device = currentDevice,
                isSimulation = isSimulation,
                onConnect = { viewModel.refresh() },
                onDisconnect = { viewModel.disconnect() },
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                onToggleSimulation = { viewModel.toggleSimulation(it) }
            )
        }

        // --- 5. Quick Navigation Tiles ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard(
                    onClick = onNavigateToLightBar,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_lightbar_tile"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "RGB Light Bar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preset & custom RGB sliders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ElevatedCard(
                    onClick = onNavigateToTester,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_tester_tile"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Input Tester",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live telemetry & stick radar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 6. Expandable DualShock 4 Setup Guide ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pairing & OTG Guide",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = { showPairingSteps = !showPairingSteps }) {
                            Text(if (showPairingSteps) "Hide" else "View")
                        }
                    }

                    AnimatedVisibility(visible = showPairingSteps) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "• Bluetooth: Hold SHARE + PS Button for 3 seconds until light bar flashes double-white strokes, then pair 'Wireless Controller' in settings.\n• USB OTG: Plug directly into Android via USB-A to Micro-USB adapter for plug-and-play zero latency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SUBCOMPONENTS
// ==========================================

@Composable
private fun DashboardHeader(
    status: ConnectionStatus,
    onRefresh: () -> Unit
) {
    val statusColor = when (status) {
        ConnectionStatus.CONNECTED -> StatusSuccess
        ConnectionStatus.CONNECTING -> StatusWarning
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 12.dp)
        ) {
            Text(
                text = "DualShock 4",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Sony PS4 Controller Hub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Connection Status Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = statusColor.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "Connected"
                        ConnectionStatus.CONNECTING -> "Connecting…"
                        ConnectionStatus.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CentralControllerIllustrationCard(
    lightBarColor: LightBarColor,
    inputState: com.example.model.InputState,
    device: DS4DeviceInfo?,
    onNavigateToLightBar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("central_controller_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clean Front-Facing Controller Vector
            DS4ControllerVector(
                lightBarColor = lightBarColor,
                inputState = inputState,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .testTag("dashboard_controller_vector")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Controller Color Swatch & Action Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(lightBarColor.toComposeColor())
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Light Bar Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lightBarColor.toHex(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = onNavigateToLightBar,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Color")
                }
            }
        }
    }
}

@Composable
private fun BatteryPlaceholderCard(
    batteryInfo: BatteryInfo,
    modifier: Modifier = Modifier
) {
    val percentage = batteryInfo.percentage
    val isCharging = batteryInfo.isCharging == true

    val statusColor = when {
        isCharging -> StatusInfo
        percentage == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percentage >= 60 -> StatusSuccess
        percentage >= 25 -> StatusWarning
        else -> StatusDanger
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, label = "dashboard_batt_color")

    Card(
        modifier = modifier
            .height(138.dp)
            .testTag("dashboard_battery_placeholder"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = when {
                        isCharging -> Icons.Default.BatteryChargingFull
                        percentage == null -> Icons.Default.BatteryUnknown
                        percentage >= 80 -> Icons.Default.BatteryFull
                        percentage >= 50 -> Icons.Default.Battery5Bar
                        percentage >= 20 -> Icons.Default.Battery3Bar
                        percentage > 0 -> Icons.Default.Battery0Bar
                        else -> Icons.Default.BatteryAlert
                    },
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = if (percentage != null) "$percentage%" else "Unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (percentage != null) animatedColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        isCharging -> "Charging"
                        batteryInfo.isStale && percentage != null -> "Last known reading"
                        batteryInfo.isCharging == false -> "Not Charging"
                        batteryInfo.status == BatteryStatus.UNAVAILABLE -> "Unavailable on Bluetooth HID"
                        else -> "Hardware telemetry active"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (percentage != null) {
                LinearProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = animatedColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun ConnectionTypePlaceholderCard(
    device: DS4DeviceInfo?,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val transport = device?.transport

    Card(
        modifier = modifier
            .height(138.dp)
            .testTag("dashboard_connection_type_placeholder"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = when (transport) {
                        TransportType.USB_OTG -> Icons.Default.Usb
                        TransportType.BLUETOOTH -> Icons.Default.Bluetooth
                        TransportType.SIMULATION -> Icons.Default.VideogameAsset
                        else -> Icons.Default.PowerSettingsNew
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = device?.name ?: if (connectionStatus == ConnectionStatus.CONNECTING) "Scanning…" else "Not Connected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "${transport?.displayName ?: "Unknown"} • Active"
                        ConnectionStatus.CONNECTING -> "Connecting…"
                        ConnectionStatus.DISCONNECTED -> "No active controller"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> StatusSuccess.copy(alpha = 0.15f)
                    ConnectionStatus.CONNECTING -> StatusWarning.copy(alpha = 0.15f)
                    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "CONNECTED"
                        ConnectionStatus.CONNECTING -> "SEARCHING"
                        ConnectionStatus.DISCONNECTED -> "STANDBY"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> StatusSuccess
                        ConnectionStatus.CONNECTING -> StatusWarning
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    status: ConnectionStatus,
    device: DS4DeviceInfo?,
    isSimulation: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_action_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status == ConnectionStatus.CONNECTED) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("dashboard_disconnect_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("dashboard_connect_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect / Scan")
                    }
                }

                FilledTonalButton(
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_bluetooth_button")
                ) {
                    Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bluetooth")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device Simulation Mode Card (clearly distinguishing simulated data from actual hardware readings)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSimulation) StatusWarning.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .border(
                        1.dp,
                        if (isSimulation) StatusWarning.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Device Simulation Mode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Testing Only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isSimulation) "Simulated virtual inputs active — not real hardware readings"
                        else "Emulates virtual DS4 events when no physical controller is attached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSimulation,
                    onCheckedChange = onToggleSimulation,
                    modifier = Modifier.testTag("dashboard_simulation_switch")
                )
            }
        }
    }
}
