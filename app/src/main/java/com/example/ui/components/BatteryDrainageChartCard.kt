package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BatteryDataPoint
import com.example.model.BatteryDrainStats
import com.example.model.BatteryInfo
import com.example.model.ConnectionStatus
import com.example.ui.theme.PSBlueLight
import com.example.ui.theme.PSCyan
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-Time Battery Drainage Over Time Line Chart.
 *
 * Implements a modern Recharts-inspired data visualization component for Android Jetpack Compose:
 * 1. Smooth gradient area chart with anti-aliased cubic Bezier curves.
 * 2. Real-time dynamic updates as DualShock 4 battery reports arrive.
 * 3. Interactive touch scrubbing with a dashed vertical cursor line and a floating Recharts-style tooltip.
 * 4. Comprehensive session KPIs: Current Level, Session Drain, Drain Rate (%/hr), and Estimated Runtime.
 * 5. Interactive controls to simulate battery drain steps or reset the session baseline.
 */
@Composable
fun BatteryDrainageChartCard(
    batteryInfo: BatteryInfo,
    batteryHistory: List<BatteryDataPoint>,
    drainStats: BatteryDrainStats,
    connectionStatus: ConnectionStatus,
    isSimulationMode: Boolean,
    onSimulateDrainStep: () -> Unit,
    onResetHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED || isSimulationMode
    val currentPercentage = batteryInfo.percentage ?: drainStats.currentPercentage ?: 85
    val isCharging = batteryInfo.isCharging == true || drainStats.isCharging

    // Pulse animation for the live status beacon
    val infiniteTransition = rememberInfiniteTransition(label = "battery_chart_pulse")
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    // Interactive scrub state: selected data point index
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_drainage_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- 1. Header Bar: Title, Subtitle & Interactive Controls ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCharging) StatusInfo.copy(alpha = 0.15f)
                                else if (isConnected) StatusSuccess.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (isCharging) StatusInfo else if (isConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Battery Drainage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Live Beacon Dot
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !isConnected -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            isCharging -> StatusInfo.copy(alpha = beaconPulse)
                                            else -> StatusSuccess.copy(alpha = beaconPulse)
                                        }
                                    )
                            )
                        }
                        Text(
                            text = if (isCharging) "Charging session • Real-time telemetry" else "Active session discharge rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Test Drain button (visible in simulation mode or when testing is enabled)
                    if (isSimulationMode || isConnected) {
                        OutlinedButton(
                            onClick = onSimulateDrainStep,
                            modifier = Modifier.testTag("battery_simulate_drain_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "-2% Drain",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Reset Session history button
                    IconButton(
                        onClick = {
                            selectedPointIndex = null
                            onResetHistory()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("battery_reset_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Session History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 2. Live Session KPI Metric Chips ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Current Battery %
                val batteryColor = when {
                    isCharging -> StatusInfo
                    currentPercentage >= 60 -> StatusSuccess
                    currentPercentage >= 25 -> StatusWarning
                    else -> StatusDanger
                }
                MetricPill(
                    label = "CURRENT",
                    value = "$currentPercentage%",
                    color = batteryColor,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Session Drain Delta
                val drainLabel = when {
                    isCharging -> "+${drainStats.totalDrain}%"
                    drainStats.totalDrain > 0 -> "-${drainStats.totalDrain}%"
                    else -> "0%"
                }
                MetricPill(
                    label = if (isCharging) "CHARGED" else "DRAIN",
                    value = drainLabel,
                    color = if (isCharging) StatusInfo else if (drainStats.totalDrain > 15) StatusWarning else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Metric 3: Drain Rate
                val rateText = if (isCharging) {
                    String.format("+%.1f%%/h", drainStats.drainRatePerHour)
                } else {
                    String.format("%.1f%%/h", drainStats.drainRatePerHour)
                }
                MetricPill(
                    label = "DRAIN RATE",
                    value = rateText,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Metric 4: Est. Remaining Runtime
                val estText = drainStats.estimatedMinutesRemaining?.let { mins ->
                    val h = mins / 60
                    val m = mins % 60
                    if (h > 0) "${h}h ${m}m" else "${m}m"
                } ?: "--"
                MetricPill(
                    label = if (isCharging) "TO FULL" else "EST. LIFE",
                    value = estText,
                    color = if (isCharging) StatusInfo else StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 3. Interactive Recharts-Style Canvas Line Chart ---
            val pointsToRender = remember(batteryHistory, currentPercentage, isCharging) {
                if (batteryHistory.isNotEmpty()) {
                    batteryHistory
                } else {
                    // Fallback baseline for visual readiness
                    val now = System.currentTimeMillis()
                    listOf(
                        BatteryDataPoint(now - 1500000L, (currentPercentage + 6).coerceIn(0, 100), isCharging),
                        BatteryDataPoint(now - 1000000L, (currentPercentage + 4).coerceIn(0, 100), isCharging),
                        BatteryDataPoint(now - 500000L, (currentPercentage + 2).coerceIn(0, 100), isCharging),
                        BatteryDataPoint(now, currentPercentage, isCharging)
                    )
                }
            }

            val selectedPoint = selectedPointIndex?.let { idx ->
                if (idx in pointsToRender.indices) pointsToRender[idx] else null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1520))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .pointerInput(pointsToRender) {
                        detectTapGestures(
                            onTap = { offset ->
                                selectedPointIndex = calculateClosestPointIndex(offset.x, size.width.toFloat(), pointsToRender.size)
                            }
                        )
                    }
                    .pointerInput(pointsToRender) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                selectedPointIndex = calculateClosestPointIndex(offset.x, size.width.toFloat(), pointsToRender.size)
                            },
                            onDrag = { change, _ ->
                                selectedPointIndex = calculateClosestPointIndex(change.position.x, size.width.toFloat(), pointsToRender.size)
                            },
                            onDragEnd = {
                                // Keep the tooltip visible for reading
                            },
                            onDragCancel = {
                                selectedPointIndex = null
                            }
                        )
                    }
                    .testTag("battery_drain_canvas")
            ) {
                val lineColor = if (isCharging) PSCyan else PSBlueLight
                val secondaryColor = if (isCharging) StatusInfo else Color(0xFF00E5FF)
                val gridColor = Color.White.copy(alpha = 0.08f)
                val labelTextColor = android.graphics.Color.argb(120, 255, 255, 255)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 26.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Horizontal Reference Gridlines (100%, 75%, 50%, 25%, 0%)
                    val yPercentages = listOf(100, 75, 50, 25, 0)
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    yPercentages.forEach { pct ->
                        val y = h * (1f - (pct / 100f))
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = if (pct == 0 || pct == 100) null else dashPathEffect
                        )

                        // Y-Axis label drawing with native canvas
                        val paint = android.graphics.Paint().apply {
                            color = labelTextColor
                            textSize = 24f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                        drawContext.canvas.nativeCanvas.drawText("$pct%", 4f, (y - 4f).coerceAtLeast(20f), paint)
                    }

                    if (pointsToRender.size >= 2) {
                        // 2. Map Battery Points to Pixel Coordinates
                        val mappedPoints = pointsToRender.mapIndexed { index, point ->
                            val x = (w * index / (pointsToRender.size - 1).toFloat()).coerceIn(0f, w)
                            val y = (h * (1f - (point.percentage.coerceIn(0, 100) / 100f))).coerceIn(0f, h)
                            Offset(x, y)
                        }

                        // 3. Build Smooth Cubic Bezier Curves (Recharts spline style)
                        val strokePath = Path()
                        val fillPath = Path()

                        strokePath.moveTo(mappedPoints.first().x, mappedPoints.first().y)
                        fillPath.moveTo(mappedPoints.first().x, h)
                        fillPath.lineTo(mappedPoints.first().x, mappedPoints.first().y)

                        for (i in 0 until mappedPoints.size - 1) {
                            val p0 = mappedPoints[i]
                            val p1 = mappedPoints[i + 1]
                            val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                            val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)

                            strokePath.cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                p1.x, p1.y
                            )
                            fillPath.cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                p1.x, p1.y
                            )
                        }

                        fillPath.lineTo(mappedPoints.last().x, h)
                        fillPath.close()

                        // 4. Area Gradient Fill under the curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.32f),
                                    lineColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )

                        // 5. Glowing Stroke Line
                        drawPath(
                            path = strokePath,
                            color = lineColor.copy(alpha = 0.35f),
                            style = Stroke(width = 7f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )

                        // 6. Data Point Dots
                        mappedPoints.forEachIndexed { idx, pt ->
                            val isSelected = selectedPointIndex == idx
                            val radius = if (isSelected) 6f else 3.5f

                            if (isSelected) {
                                drawCircle(
                                    color = secondaryColor.copy(alpha = 0.35f),
                                    radius = 14f,
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = if (isSelected) secondaryColor else lineColor,
                                radius = radius,
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = radius * 0.5f,
                                center = pt
                            )
                        }

                        // 7. Interactive Scrub Cursor Line
                        selectedPointIndex?.let { selIdx ->
                            if (selIdx in mappedPoints.indices) {
                                val selPt = mappedPoints[selIdx]
                                drawLine(
                                    color = Color.White.copy(alpha = 0.5f),
                                    start = Offset(selPt.x, 0f),
                                    end = Offset(selPt.x, h),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                )
                            }
                        }
                    }

                    // 8. Bottom X-Axis Time Labels
                    val timePaint = android.graphics.Paint().apply {
                        color = labelTextColor
                        textSize = 22f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val nowTextY = h + 22f
                    drawContext.canvas.nativeCanvas.drawText("Session Start", 36f, nowTextY, timePaint)
                    drawContext.canvas.nativeCanvas.drawText("Mid-Session", w * 0.5f, nowTextY, timePaint)
                    drawContext.canvas.nativeCanvas.drawText("Now", w - 24f, nowTextY, timePaint)
                }

                // --- Floating Interactive Recharts Tooltip ---
                if (selectedPoint != null && selectedPointIndex != null) {
                    val idx = selectedPointIndex!!
                    val isNearRight = idx > pointsToRender.size / 2
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val timeString = timeFormat.format(Date(selectedPoint.timestamp))

                    Box(
                        modifier = Modifier
                            .align(if (isNearRight) Alignment.TopStart else Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.94f))
                            .border(1.dp, lineColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(lineColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Battery: ${selectedPoint.percentage}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Time: $timeString • ${if (selectedPoint.isCharging == true) "Charging" else "Discharging"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Scrub hint
                    Text(
                        text = "Touch or drag to scrub points",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 4. Hardware Footer Callout ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DS4 Li-ion 1000mAh • 3.65V nominal",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isCharging) "Charging active" else "Average: 8–15%/hr",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCharging) StatusInfo else MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun calculateClosestPointIndex(touchX: Float, totalWidth: Float, pointCount: Int): Int {
    if (pointCount <= 1 || totalWidth <= 0f) return 0
    val normalized = (touchX / totalWidth).coerceIn(0f, 1f)
    return (normalized * (pointCount - 1)).toInt().coerceIn(0, pointCount - 1)
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}
