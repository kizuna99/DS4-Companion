package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DEFAULT_PRESETS
import com.example.model.InputState
import com.example.model.LightBarColor
import com.example.model.LightBarPreset
import com.example.model.TransportType
import com.example.ui.components.DS4ControllerVector
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.viewmodel.DS4ViewModel
import kotlin.math.roundToInt

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightBarScreen(
    viewModel: DS4ViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeColor by viewModel.lightBarColor.collectAsStateWithLifecycle()
    val applyResult by viewModel.lightBarResult.collectAsStateWithLifecycle()
    val currentDevice by viewModel.currentDevice.collectAsStateWithLifecycle()

    var selectedRed by remember(activeColor) { mutableIntStateOf(activeColor.red) }
    var selectedGreen by remember(activeColor) { mutableIntStateOf(activeColor.green) }
    var selectedBlue by remember(activeColor) { mutableIntStateOf(activeColor.blue) }
    var selectedBrightness by remember(activeColor) { mutableFloatStateOf(activeColor.brightness) }

    val liveColor = remember(selectedRed, selectedGreen, selectedBlue, selectedBrightness) {
        LightBarColor(
            red = selectedRed,
            green = selectedGreen,
            blue = selectedBlue,
            brightness = selectedBrightness
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("lightbar_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optional Back Button Navigation Header
        if (onNavigateBack != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("lightbar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Light Bar Color",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. Controller Preview with real-time RGB light bar
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
                    Text(
                        text = "Live Light Bar Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Previewing ${liveColor.toHex()} • ${(liveColor.brightness * 100).toInt()}% brightness",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DS4ControllerVector(
                        lightBarColor = liveColor,
                        inputState = InputState(),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .testTag("lightbar_preview_controller")
                    )
                }
            }
        }

        // 2. Hardware Feedback / Result Banner
        item {
            applyResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            StatusSuccess.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (result.success) StatusSuccess else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (result.success) "Hardware Command Sent" else "Hardware Protocol Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (result.success) StatusSuccess else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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

        // 3. Preset Colors
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Color Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DEFAULT_PRESETS.forEach { preset ->
                            val isSelected = selectedRed == preset.color.red &&
                                    selectedGreen == preset.color.green &&
                                    selectedBlue == preset.color.blue

                            PresetChip(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = {
                                    selectedRed = preset.color.red
                                    selectedGreen = preset.color.green
                                    selectedBlue = preset.color.blue
                                    viewModel.applyColor(preset.color.copy(brightness = selectedBrightness))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Fine-Tune Sliders (RGB + Brightness)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RGB Sliders & Brightness",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(liveColor.toComposeColor())
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Red Slider
                    ColorSlider(
                        label = "Red (R)",
                        value = selectedRed,
                        onValueChange = { selectedRed = it },
                        thumbColor = Color(0xFFFF5252),
                        trackColor = Color(0xFFFF8A80)
                    )

                    // Green Slider
                    ColorSlider(
                        label = "Green (G)",
                        value = selectedGreen,
                        onValueChange = { selectedGreen = it },
                        thumbColor = Color(0xFF00E676),
                        trackColor = Color(0xFFB9F6CA)
                    )

                    // Blue Slider
                    ColorSlider(
                        label = "Blue (B)",
                        value = selectedBlue,
                        onValueChange = { selectedBlue = it },
                        thumbColor = Color(0xFF448AFF),
                        trackColor = Color(0xFF82B1FF)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Brightness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brightness", style = MaterialTheme.typography.labelMedium)
                        Text("${(selectedBrightness * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = selectedBrightness,
                        onValueChange = { selectedBrightness = it },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brightness_slider")
                    )
                }
            }
        }

        // 5. Actions (Apply & Reset)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val defaultColor = LightBarColor.PLAYSTATION_BLUE
                        selectedRed = defaultColor.red
                        selectedGreen = defaultColor.green
                        selectedBlue = defaultColor.blue
                        selectedBrightness = defaultColor.brightness
                        viewModel.resetColor()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_color_button")
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Blue")
                }

                Button(
                    onClick = {
                        viewModel.applyColor(liveColor)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("apply_color_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Color")
                }
            }
        }

        // 6. Protocol Note Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hardware Protocol Notice",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• USB OTG: Fully supported via direct DualShock 4 HID Output Report 0x05.\n• Bluetooth: Standard Android OS isolates raw Bluetooth HID sockets. On unrooted Android, pairing via USB OTG is recommended for instantaneous hardware LED programming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: LightBarPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("preset_${preset.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(preset.color.toComposeColor())
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    thumbColor: Color,
    trackColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = "$value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = trackColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("slider_${label.take(1)}")
        )
    }
}
