package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CenteringStatus
import com.example.model.DiagnosticsState
import com.example.model.MotionSensorsState
import com.example.protocol.MotorSelection
import com.example.protocol.VibrationTestResult
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.DS4ViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DS4ViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val diagState by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val currentDevice by viewModel.currentDevice.collectAsStateWithLifecycle()
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    val vibrationResult by viewModel.vibrationResult.collectAsStateWithLifecycle()
    val isVibrating by viewModel.isVibrating.collectAsStateWithLifecycle()
    val isVibrationEnabled by viewModel.isVibrationEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optional Back Button Header
        if (onNavigateBack != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Controller Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. Controller Hardware Specs Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hardware Identity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearDiagnostics() },
                            modifier = Modifier.testTag("clear_diagnostics_button")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Results")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    DiagRow("Device Name", currentDevice?.name ?: "No device connected")
                    DiagRow("Hardware Model", currentDevice?.modelName ?: "Unknown")
                    DiagRow("Connection Transport", currentDevice?.transport?.displayName ?: "-")
                    DiagRow("Vendor ID (Sony)", diagState.vendorIdHex)
                    DiagRow("Product ID (DS4)", diagState.productIdHex)
                    DiagRow("Input Device ID", currentDevice?.inputDeviceId?.toString() ?: "None")
                    DiagRow("Input Sources", diagState.inputSourcesDescription)
                    DiagRow("Total Telemetry Events", "${diagState.totalEventsCount}")
                }
            }
        }

        // 2. Dual-Motor Vibration Test & On/Off Toggle Card
        item {
            VibrationDiagnosticCard(
                viewModel = viewModel,
                vibrationResult = vibrationResult,
                isVibrating = isVibrating,
                isVibrationEnabled = isVibrationEnabled
            )
        }

        // 3. 6-Axis Motion Sensor (IMU) Health Check
        item {
            MotionSensorHealthCard(sensors = inputState.motionSensors)
        }

        // 4. Real-Time Stick Centering & Drift Telemetry
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analog Stick Centering & Drift Check",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-time deadzone & resting position analysis. Green indicates ideal factory centering (< 0.12).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StickDriftPill(
                            title = "Left Stick (LS)",
                            status = diagState.leftStickCenteringStatus,
                            offset = diagState.leftStickDriftOffset,
                            modifier = Modifier.weight(1f)
                        )
                        StickDriftPill(
                            title = "Right Stick (RS)",
                            status = diagState.rightStickCenteringStatus,
                            offset = diagState.rightStickDriftOffset,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Stuck Button Detector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (diagState.stuckButtons.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (diagState.stuckButtons.isEmpty()) StatusSuccess else StatusDanger
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stuck Button Detector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (diagState.stuckButtons.isEmpty()) {
                        Text(
                            text = "All buttons released normally. No stuck or unreleased inputs detected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Warning: The following buttons have been held continuously or failed to release:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusDanger,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            diagState.stuckButtons.forEach { btn ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusDanger.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = btn, color = StatusDanger, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Detected Inputs Matrix in current session
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Inputs Confirmed Functional (${diagState.detectedInputs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (diagState.detectedInputs.isEmpty()) {
                        Text(
                            text = "No inputs tested yet. Go to the Input Test screen or press buttons to verify each control.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            diagState.detectedInputs.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StatusSuccess.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "✓ $item",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Troubleshooting & Compatibility Guide
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Android Driver Reference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    BulletPoint(
                        title = "Input Polling Frequency",
                        desc = "Android Bluetooth typically operates at 60-120Hz polling. If you experience high latency, disconnect other Bluetooth audio accessories."
                    )
                    BulletPoint(
                        title = "Light Bar & Battery on Bluetooth",
                        desc = "Android 12+ supports LightsManager and InputDevice.batteryState natively. On older Android versions, connect via USB OTG for unrestricted hardware HID communication."
                    )
                    BulletPoint(
                        title = "PS Home Button Behavior",
                        desc = "By default on many Android ROMs, the PS button is mapped to KEYCODE_BUTTON_MODE, which the OS window manager intercepts as Home. This is normal Android OS behavior."
                    )
                }
            }
        }
    }
}

// ==========================================
// SUBCOMPONENTS
// ==========================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VibrationDiagnosticCard(
    viewModel: DS4ViewModel,
    vibrationResult: VibrationTestResult?,
    isVibrating: Boolean,
    isVibrationEnabled: Boolean
) {
    var selectedMotor by remember { mutableStateOf(MotorSelection.BOTH) }
    var selectedDurationMs by remember { mutableLongStateOf(1000L) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vibration_diagnostic_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = if (isVibrating) StatusWarning else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Vibration & Haptics Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isVibrating) "Motors buzzing..." else "Left (Heavy) + Right (Light) rumble",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isVibrating) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option to ON/OFF vibration
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isVibrationEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isVibrationEnabled) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isVibrationEnabled,
                        onCheckedChange = { viewModel.setVibrationEnabled(it) },
                        modifier = Modifier.testTag("vibration_on_off_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Motor Selector Chips
            Text(
                text = "Motor Selection",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MotorSelection.values().forEach { motor ->
                    FilterChip(
                        selected = selectedMotor == motor,
                        onClick = { selectedMotor = motor },
                        label = { Text(motor.label) },
                        modifier = Modifier.testTag("motor_chip_${motor.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duration Selector Chips
            Text(
                text = "Duration",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L to "500ms", 1000L to "1.0s", 2000L to "2.0s").forEach { (duration, label) ->
                    FilterChip(
                        selected = selectedDurationMs == duration,
                        onClick = { selectedDurationMs = duration },
                        label = { Text(label) },
                        modifier = Modifier.testTag("duration_chip_$duration")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Vibration Action Buttons (Start / Stop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.testVibration(
                            durationMs = selectedDurationMs,
                            motor = selectedMotor
                        )
                    },
                    enabled = isVibrationEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_vibration_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Vibration")
                }

                if (isVibrating) {
                    Button(
                        onClick = { viewModel.stopVibration() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                        modifier = Modifier.testTag("stop_vibration_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }

            // Result Banner
            AnimatedVisibility(visible = vibrationResult != null) {
                vibrationResult?.let { result ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (result.success) StatusSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (result.success) StatusSuccess else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionSensorHealthCard(sensors: MotionSensorsState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("motion_sensor_health_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Motion Sensors (IMU)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sensors.isAvailable) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (sensors.isAvailable) "Active" else "Idle",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (sensors.isAvailable) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DiagRow("Status", sensors.sensorStatus)
            DiagRow("Accelerometer", "X:${String.format("%.2f", sensors.accelX)} Y:${String.format("%.2f", sensors.accelY)} Z:${String.format("%.2f", sensors.accelZ)} m/s²")
            DiagRow("Gyroscope", "X:${String.format("%.2f", sensors.gyroX)} Y:${String.format("%.2f", sensors.gyroY)} Z:${String.format("%.2f", sensors.gyroZ)} rad/s")
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StickDriftPill(
    title: String,
    status: CenteringStatus,
    offset: Float,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        CenteringStatus.NEUTRAL -> StatusSuccess
        CenteringStatus.SLIGHT_OFFSET -> StatusWarning
        CenteringStatus.SEVERE_DRIFT -> StatusDanger
        CenteringStatus.ACTIVE_INPUT -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(statusColor.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status.label, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
            Text(
                text = "Offset: ${String.format("%.3f", offset)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BulletPoint(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "• $title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
