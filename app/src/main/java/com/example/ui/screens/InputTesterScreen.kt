package com.example.ui.screens

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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.InputLogEvent
import com.example.model.InputState
import com.example.ui.components.DS4ControllerVector
import com.example.ui.theme.PSCircleRed
import com.example.ui.theme.PSCrossBlue
import com.example.ui.theme.PSSquarePink
import com.example.ui.theme.PSTriangleGreen
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.DS4ViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputTesterScreen(
    viewModel: DS4ViewModel,
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
        // 1. Controller Visual Representation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DualShock 4 Interactive Board",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { viewModel.resetInputTester() },
                            modifier = Modifier.testTag("reset_test_button")
                        ) {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Test")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DS4ControllerVector(
                        lightBarColor = lightBarColor,
                        inputState = inputState,
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .testTag("tester_controller_vector")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Buttons and sticks highlight in real-time when pressed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Analog Sticks & Triggers Telemetry
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

        // 3. Triggers (L2 / R2) Analog Pressure
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Analog Triggers Pressure",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // L2 Trigger
                    TriggerPressureBar(
                        label = "L2 Trigger",
                        pressure = inputState.l2Pressure,
                        isButtonPressed = inputState.buttonL2
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // R2 Trigger
                    TriggerPressureBar(
                        label = "R2 Trigger",
                        pressure = inputState.r2Pressure,
                        isButtonPressed = inputState.buttonR2
                    )
                }
            }
        }

        // 4. Quick Buttons Status Matrix
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "All Buttons Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ButtonPill("✕ Cross", inputState.buttonCross, PSCrossBlue)
                        ButtonPill("◯ Circle", inputState.buttonCircle, PSCircleRed)
                        ButtonPill("□ Square", inputState.buttonSquare, PSSquarePink)
                        ButtonPill("△ Triangle", inputState.buttonTriangle, PSTriangleGreen)
                        ButtonPill("L1", inputState.buttonL1, MaterialTheme.colorScheme.primary)
                        ButtonPill("R1", inputState.buttonR1, MaterialTheme.colorScheme.primary)
                        ButtonPill("L2", inputState.buttonL2 || inputState.l2Pressure > 0.2f, MaterialTheme.colorScheme.primary)
                        ButtonPill("R2", inputState.buttonR2 || inputState.r2Pressure > 0.2f, MaterialTheme.colorScheme.primary)
                        ButtonPill("L3 (Stick)", inputState.buttonL3, MaterialTheme.colorScheme.primary)
                        ButtonPill("R3 (Stick)", inputState.buttonR3, MaterialTheme.colorScheme.primary)
                        ButtonPill("▲ Up", inputState.dpadUp, MaterialTheme.colorScheme.secondary)
                        ButtonPill("▼ Down", inputState.dpadDown, MaterialTheme.colorScheme.secondary)
                        ButtonPill("◀ Left", inputState.dpadLeft, MaterialTheme.colorScheme.secondary)
                        ButtonPill("▶ Right", inputState.dpadRight, MaterialTheme.colorScheme.secondary)
                        ButtonPill("Share", inputState.buttonShare, MaterialTheme.colorScheme.tertiary)
                        ButtonPill("Options", inputState.buttonOptions, MaterialTheme.colorScheme.tertiary)
                        ButtonPill("PS Button", inputState.buttonPs, MaterialTheme.colorScheme.primary)
                        ButtonPill("Touchpad", inputState.buttonTouchpad, MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // 5. Live Input Event Telemetry Stream
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
                            text = "Live Input Event Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${inputState.recentEvents.size} events",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (inputState.recentEvents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Awaiting controller inputs… Press buttons to view telemetry.",
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
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // 2D Stick Position Box
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Crosshairs
                Box(
                    modifier = Modifier
                        .size(1.dp, 72.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .size(72.dp, 1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )

                // Stick Dot
                val maxOffset = 24.dp
                val dotOffsetX = maxOffset * x.coerceIn(-1f, 1f)
                val dotOffsetY = maxOffset * y.coerceIn(-1f, 1f)

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isClicked) MaterialTheme.colorScheme.primary else Color(0xFF00E5FF))
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "X: ${String.format("%+.2f", x)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Y: ${String.format("%+.2f", y)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
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
    val effectivePressure = if (pressure > 0f) pressure else if (isButtonPressed) 1f else 0f
    val percentInt = (effectivePressure * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$percentInt%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (effectivePressure > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
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
private fun ButtonPill(
    label: String,
    isPressed: Boolean,
    activeColor: Color
) {
    val bg = if (isPressed) activeColor else MaterialTheme.colorScheme.surfaceVariant
    val textCol = if (isPressed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                1.dp,
                if (isPressed) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isPressed) FontWeight.Bold else FontWeight.Medium,
            color = textCol
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
