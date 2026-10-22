package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.InputLogEvent
import com.example.model.InputState
import com.example.model.MotionSensorsState
import com.example.model.TouchpadState
import com.example.ui.components.DS4ControllerVector
import com.example.ui.theme.PSCircleRed
import com.example.ui.theme.PSCrossBlue
import com.example.ui.theme.PSSquarePink
import com.example.ui.theme.PSTriangleGreen
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.DS4ViewModel

/**
 * Dedicated Controller Testing Screen.
 *
 * Features:
 * 1. Clean, full-width front-facing Sony DualShock 4 vector illustration with real-time interactive highlights
 *    directly on physical button locations (NO annotation lines or external callouts).
 * 2. Independent analog stick movement according to normalized X/Y input values.
 * 3. Analog trigger (L2/R2) pressure bars showing real hardware pressure values.
 * 4. Compact live input status indicator showing active inputs.
 * 5. Small event log displaying recent press and release events.
 * 6. "Reset Test" button that clears test history without affecting connection.
 * 7. Real-time Capacitive Touchpad 2D visualizer and 6-Axis Motion Sensor health check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputTesterScreen(
    viewModel: DS4ViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    val lightBarColor by viewModel.lightBarColor.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("input_tester_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Bar ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("tester_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = "Input Test",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Interactive hardware test board",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Reset Test Button
                OutlinedButton(
                    onClick = { viewModel.resetInputTester() },
                    modifier = Modifier.testTag("reset_test_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Test", maxLines = 1, softWrap = false)
                }
            }
        }

        // --- 1. Large Dedicated DualShock 4 Controller Testing Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interactive_controller_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Full-width front-facing DualShock 4 illustration with direct highlights
                    DS4ControllerVector(
                        lightBarColor = lightBarColor,
                        inputState = inputState,
                        modifier = Modifier
                            .fillMaxWidth(0.98f)
                            .testTag("tester_controller_vector")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Inputs light up directly on the controller in real-time. Sticks deflect with physical position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- 2. Analog Triggers (L2 / R2) Pressure Visualization ---
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
                        Text(
                            text = "Analog Triggers Pressure",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (inputState.l2Pressure > 0f || inputState.r2Pressure > 0f) "Hardware Analog" else "Digital/Analog",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // L2 Trigger Pressure Bar
                    TriggerPressureBar(
                        label = "L2 Trigger",
                        pressure = inputState.l2Pressure,
                        isButtonPressed = inputState.buttonL2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // R2 Trigger Pressure Bar
                    TriggerPressureBar(
                        label = "R2 Trigger",
                        pressure = inputState.r2Pressure,
                        isButtonPressed = inputState.buttonR2
                    )
                }
            }
        }

        // --- 3. Analog Sticks Normalized Axis Coordinates Telemetry ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Stick Telemetry
                StickTelemetryCard(
                    title = "Left Stick (L3)",
                    x = inputState.leftStickX,
                    y = inputState.leftStickY,
                    isClicked = inputState.buttonL3,
                    modifier = Modifier.weight(1f)
                )

                // Right Stick Telemetry
                StickTelemetryCard(
                    title = "Right Stick (R3)",
                    x = inputState.rightStickX,
                    y = inputState.rightStickY,
                    isClicked = inputState.buttonR3,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 4. Compact Live Input Status Section ---
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
                        Text(
                            text = "Live Active Input Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (inputState.anyButtonPressed) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (inputState.anyButtonPressed) "INPUT DETECTED" else "NEUTRAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (inputState.anyButtonPressed) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val activeButtons = buildList {
                        if (inputState.buttonCross) add("Cross ✕")
                        if (inputState.buttonCircle) add("Circle ◯")
                        if (inputState.buttonSquare) add("Square □")
                        if (inputState.buttonTriangle) add("Triangle △")
                        if (inputState.buttonL1) add("L1")
                        if (inputState.buttonR1) add("R1")
                        if (inputState.buttonL2 || inputState.l2Pressure > 0.2f) add("L2")
                        if (inputState.buttonR2 || inputState.r2Pressure > 0.2f) add("R2")
                        if (inputState.buttonL3) add("L3 Click")
                        if (inputState.buttonR3) add("R3 Click")
                        if (inputState.dpadUp) add("D-Pad Up")
                        if (inputState.dpadDown) add("D-Pad Down")
                        if (inputState.dpadLeft) add("D-Pad Left")
                        if (inputState.dpadRight) add("D-Pad Right")
                        if (inputState.buttonShare) add("Share")
                        if (inputState.buttonOptions) add("Options")
                        if (inputState.buttonPs) add("PS Button")
                        if (inputState.buttonTouchpad || inputState.touchpad.isClicked) add("Touchpad Click")
                    }

                    if (activeButtons.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All controls neutral — press buttons or move sticks to verify.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeButtons.forEach { btnName ->
                                ActiveInputBadge(name = btnName)
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Capacitive Touchpad 2D Visualizer ---
        item {
            TouchpadTelemetryCard(
                touchpad = inputState.touchpad,
                isTouchpadButtonPressed = inputState.buttonTouchpad
            )
        }

        // --- 6. 6-Axis Motion Sensors (IMU) Telemetry ---
        item {
            MotionSensorsTelemetryCard(
                sensors = inputState.motionSensors
            )
        }

        // --- 7. Recent Events Log Section ---
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
                        Text(
                            text = "Recent Input Event Stream",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${inputState.recentEvents.size} events recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (inputState.recentEvents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No events yet. Press buttons or move controls to begin logging.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            inputState.recentEvents.take(8).forEach { event ->
                                EventLogItem(event)
                            }
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
private fun ActiveInputBadge(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun StickTelemetryCard(
    title: String,
    x: Float,
    y: Float,
    isClicked: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (isClicked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("CLICKED", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // 2D Stick Position Box with Center Reticle
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Crosshairs
                Box(
                    modifier = Modifier
                        .size(1.dp, 76.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(76.dp, 1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )

                // Stick Deflection Dot
                val maxOffset = 24.dp
                val dotOffsetX = maxOffset * x.coerceIn(-1f, 1f)
                val dotOffsetY = maxOffset * y.coerceIn(-1f, 1f)

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isClicked) MaterialTheme.colorScheme.primary else Color(0xFF00E5FF))
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "X: ${String.format("%+.2f", x)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Y: ${String.format("%+.2f", y)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TriggerPressureBar(
    label: String,
    pressure: Float,
    isButtonPressed: Boolean
) {
    // Uses true analog pressure if available (> 0f), or digital button status
    val effectivePressure = if (pressure > 0f) pressure else if (isButtonPressed) 1f else 0f
    val percentInt = (effectivePressure * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (pressure > 0f) "$percentInt% (Analog)" else if (isButtonPressed) "Pressed (Digital)" else "0%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (effectivePressure > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { effectivePressure },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun EventLogItem(event: InputLogEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (event.action == "PRESSED") StatusSuccess else StatusWarning)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = event.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "${event.action} (${event.value})",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TouchpadTelemetryCard(
    touchpad: TouchpadState,
    isTouchpadButtonPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val isClicked = touchpad.isClicked || isTouchpadButtonPressed

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("touchpad_telemetry_card"),
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
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Capacitive Touchpad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isClicked -> StatusSuccess
                                touchpad.isTouching -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            isClicked -> "CLICKED"
                            touchpad.isTouching -> "TOUCH ACTIVE"
                            else -> "IDLE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isClicked -> Color.White
                            touchpad.isTouching -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DualShock 4 2D capacitive surface with physical tactile click.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2D Touchpad Canvas (Aspect ratio 2.1:1)
            val borderColor = if (isClicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            val padBg = if (isClicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(padBg)
                    .border(
                        width = if (isClicked) 2.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val cyanColor = Color(0xFF00E5FF)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid lines (3x2 divisions)
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    drawLine(gridColor, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(w * 0.66f, 0f), Offset(w * 0.66f, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

                    // Touch Indicator
                    val posX = (w * touchpad.x).coerceIn(12f, w - 12f)
                    val posY = (h * touchpad.y).coerceIn(12f, h - 12f)

                    if (touchpad.isTouching || isClicked) {
                        drawCircle(
                            color = (if (isClicked) primaryColor else cyanColor).copy(alpha = 0.35f),
                            radius = 24f,
                            center = Offset(posX, posY)
                        )
                        drawCircle(
                            color = if (isClicked) primaryColor else cyanColor,
                            radius = 12f,
                            center = Offset(posX, posY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = Offset(posX, posY)
                        )
                        val crossColor = (if (isClicked) primaryColor else cyanColor).copy(alpha = 0.4f)
                        drawLine(crossColor, Offset(0f, posY), Offset(w, posY), strokeWidth = 1.5f)
                        drawLine(crossColor, Offset(posX, 0f), Offset(posX, h), strokeWidth = 1.5f)
                    } else {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = 6f,
                            center = Offset(w * 0.5f, h * 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Coordinates Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TouchMetricPill(
                    label = "Touch X",
                    value = String.format("%.3f", touchpad.x),
                    subValue = "${(touchpad.x * 1920).toInt()} px",
                    modifier = Modifier.weight(1f)
                )

                TouchMetricPill(
                    label = "Touch Y",
                    value = String.format("%.3f", touchpad.y),
                    subValue = "${(touchpad.y * 942).toInt()} px",
                    modifier = Modifier.weight(1f)
                )

                TouchMetricPill(
                    label = "Tactile Click",
                    value = if (isClicked) "ACTIVE" else "RELEASED",
                    subValue = if (touchpad.touchCount > 0) "${touchpad.touchCount} finger(s)" else "0 fingers",
                    isActive = isClicked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TouchMetricPill(
    label: String,
    value: String,
    subValue: String,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MotionSensorsTelemetryCard(
    sensors: MotionSensorsState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("motion_sensors_telemetry_card"),
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
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "6-Axis Motion Sensors (IMU)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sensors.sensorStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Live telemetry from 3-axis accelerometer and 3-axis gyroscope.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Accelerometer (Linear Acceleration m/s²)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SensorAxisItem("Accel X", sensors.accelX, "m/s²", -19.6f, 19.6f, Color(0xFF00E5FF), Modifier.weight(1f))
                SensorAxisItem("Accel Y", sensors.accelY, "m/s²", -19.6f, 19.6f, Color(0xFF69F0AE), Modifier.weight(1f))
                SensorAxisItem("Accel Z", sensors.accelZ, "m/s²", -19.6f, 19.6f, Color(0xFFFFD740), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Gyroscope (Angular Velocity rad/s)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SensorAxisItem("Gyro X (Pitch)", sensors.gyroX, "rad/s", -8f, 8f, Color(0xFFFF5252), Modifier.weight(1f))
                SensorAxisItem("Gyro Y (Roll)", sensors.gyroY, "rad/s", -8f, 8f, Color(0xFFE040FB), Modifier.weight(1f))
                SensorAxisItem("Gyro Z (Yaw)", sensors.gyroZ, "rad/s", -8f, 8f, Color(0xFF7C4DFF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SensorAxisItem(
    label: String,
    value: Float,
    unit: String,
    minVal: Float,
    maxVal: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val range = maxVal - minVal
    val normalized = if (range != 0f) ((value - minVal) / range).coerceIn(0f, 1f) else 0.5f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format("%+.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { normalized },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
