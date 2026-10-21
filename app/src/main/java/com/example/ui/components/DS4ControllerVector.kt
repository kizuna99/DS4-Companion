package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.InputState
import com.example.model.LightBarColor
import com.example.ui.theme.PSCircleRed
import com.example.ui.theme.PSCrossBlue
import com.example.ui.theme.PSSquarePink
import com.example.ui.theme.PSTriangleGreen

@Composable
fun DS4ControllerVector(
    lightBarColor: LightBarColor,
    inputState: InputState,
    modifier: Modifier = Modifier,
    interactiveMode: Boolean = true
) {
    val animatedLightColor by animateColorAsState(
        targetValue = lightBarColor.toComposeColor(),
        animationSpec = tween(durationMillis = 200),
        label = "lightbar_color_anim"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val activeAccentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // 1. Draw Controller Main Outer Shell (Handles & Center Body)
            drawControllerBody(w, h, surfaceColor, outlineColor)

            // 2. Draw Glowing Light Bar at top center
            drawLightBar(w, h, animatedLightColor)

            // 3. Draw Touchpad Plate
            drawTouchpad(w, h, inputState.buttonTouchpad, outlineColor, activeAccentColor)

            // 4. Draw D-Pad
            drawDPad(w, h, inputState, outlineColor, activeAccentColor)

            // 5. Draw Action Buttons (Cross, Circle, Square, Triangle)
            drawActionButtons(w, h, inputState, outlineColor)

            // 6. Draw Shoulder Buttons & Triggers (L1, R1, L2, R2)
            drawShoulderButtons(w, h, inputState, activeAccentColor)

            // 7. Draw Analog Sticks (Left & Right) with Deflection
            drawAnalogSticks(w, h, inputState, outlineColor, activeAccentColor)

            // 8. Draw Center Buttons (Share, Options, PS Logo)
            drawCenterButtons(w, h, inputState, outlineColor, activeAccentColor)
        }
    }
}

private fun DrawScope.drawControllerBody(w: Float, h: Float, bodyColor: Color, outlineColor: Color) {
    val path = Path().apply {
        // Top edge left
        moveTo(w * 0.22f, h * 0.16f)
        // Light bar dip
        lineTo(w * 0.36f, h * 0.16f)
        lineTo(w * 0.64f, h * 0.16f)
        lineTo(w * 0.78f, h * 0.16f)
        // Right shoulder curve
        cubicTo(w * 0.88f, h * 0.18f, w * 0.96f, h * 0.32f, w * 0.95f, h * 0.55f)
        // Right grip outer
        cubicTo(w * 0.94f, h * 0.75f, w * 0.88f, h * 0.96f, w * 0.80f, h * 0.96f)
        // Right grip bottom & inner curve
        cubicTo(w * 0.73f, h * 0.96f, w * 0.71f, h * 0.78f, w * 0.64f, h * 0.70f)
        // Center bottom under PS button
        cubicTo(w * 0.58f, h * 0.66f, w * 0.42f, h * 0.66f, w * 0.36f, h * 0.70f)
        // Left grip inner curve
        cubicTo(w * 0.29f, h * 0.78f, w * 0.27f, h * 0.96f, w * 0.20f, h * 0.96f)
        // Left grip bottom & outer
        cubicTo(w * 0.12f, h * 0.96f, w * 0.06f, h * 0.75f, w * 0.05f, h * 0.55f)
        // Left shoulder curve
        cubicTo(w * 0.04f, h * 0.32f, w * 0.12f, h * 0.18f, w * 0.22f, h * 0.16f)
        close()
    }

    drawPath(path, bodyColor, style = Fill)
    drawPath(path, outlineColor.copy(alpha = 0.6f), style = Stroke(width = 3.5f))
}

private fun DrawScope.drawLightBar(w: Float, h: Float, color: Color) {
    val barWidth = w * 0.26f
    val barHeight = h * 0.06f
    val left = (w - barWidth) / 2f
    val top = h * 0.13f

    // Outer luminous bloom / glow
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(w * 0.5f, top + barHeight / 2f),
            radius = barWidth * 0.8f
        ),
        topLeft = Offset(left - 18f, top - 12f),
        size = Size(barWidth + 36f, barHeight + 24f),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Solid core light bar
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Highlight reflection
    drawRoundRect(
        color = Color.White.copy(alpha = 0.5f),
        topLeft = Offset(left + 6f, top + 2f),
        size = Size(barWidth - 12f, barHeight * 0.35f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

private fun DrawScope.drawTouchpad(w: Float, h: Float, isPressed: Boolean, outlineColor: Color, activeColor: Color) {
    val padWidth = w * 0.24f
    val padHeight = h * 0.26f
    val left = (w - padWidth) / 2f
    val top = h * 0.21f

    val fill = if (isPressed) activeColor.copy(alpha = 0.35f) else outlineColor.copy(alpha = 0.15f)
    val border = if (isPressed) activeColor else outlineColor.copy(alpha = 0.5f)

    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(padWidth, padHeight),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(left, top),
        size = Size(padWidth, padHeight),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = if (isPressed) 3f else 1.5f)
    )
}

private fun DrawScope.drawDPad(w: Float, h: Float, input: InputState, outlineColor: Color, activeColor: Color) {
    val cx = w * 0.23f
    val cy = h * 0.44f
    val armLength = w * 0.045f
    val armWidth = w * 0.038f

    // Center hub
    drawCircle(
        color = outlineColor.copy(alpha = 0.2f),
        radius = armWidth * 0.7f,
        center = Offset(cx, cy)
    )

    fun drawArm(offset: Offset, isPressed: Boolean, isHorizontal: Boolean) {
        val size = if (isHorizontal) Size(armLength, armWidth) else Size(armWidth, armLength)
        val fill = if (isPressed) activeColor else outlineColor.copy(alpha = 0.3f)
        drawRoundRect(
            color = fill,
            topLeft = offset,
            size = size,
            cornerRadius = CornerRadius(4f, 4f)
        )
        if (isPressed) {
            drawRoundRect(
                color = Color.White,
                topLeft = offset,
                size = size,
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 2f)
            )
        }
    }

    // Up
    drawArm(Offset(cx - armWidth / 2f, cy - armLength - armWidth / 3f), input.dpadUp, false)
    // Down
    drawArm(Offset(cx - armWidth / 2f, cy + armWidth / 3f), input.dpadDown, false)
    // Left
    drawArm(Offset(cx - armLength - armWidth / 3f, cy - armWidth / 2f), input.dpadLeft, true)
    // Right
    drawArm(Offset(cx + armWidth / 3f, cy - armWidth / 2f), input.dpadRight, true)
}

private fun DrawScope.drawActionButtons(w: Float, h: Float, input: InputState, outlineColor: Color) {
    val cx = w * 0.77f
    val cy = h * 0.44f
    val spacing = w * 0.045f
    val radius = w * 0.022f

    fun drawBtn(center: Offset, isPressed: Boolean, brandColor: Color, symbol: String) {
        val fill = if (isPressed) brandColor else outlineColor.copy(alpha = 0.25f)
        drawCircle(color = fill, radius = if (isPressed) radius * 1.15f else radius, center = center)
        drawCircle(
            color = if (isPressed) Color.White else brandColor,
            radius = if (isPressed) radius * 1.15f else radius,
            center = center,
            style = Stroke(width = 2.5f)
        )

        // Draw geometric symbol
        when (symbol) {
            "cross" -> {
                val d = radius * 0.5f
                drawLine(
                    color = if (isPressed) Color.White else brandColor,
                    start = Offset(center.x - d, center.y - d),
                    end = Offset(center.x + d, center.y + d),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = if (isPressed) Color.White else brandColor,
                    start = Offset(center.x + d, center.y - d),
                    end = Offset(center.x - d, center.y + d),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            "circle" -> {
                drawCircle(
                    color = if (isPressed) Color.White else brandColor,
                    radius = radius * 0.5f,
                    center = center,
                    style = Stroke(width = 2.5f)
                )
            }
            "square" -> {
                val d = radius * 0.45f
                drawRect(
                    color = if (isPressed) Color.White else brandColor,
                    topLeft = Offset(center.x - d, center.y - d),
                    size = Size(d * 2, d * 2),
                    style = Stroke(width = 2.5f)
                )
            }
            "triangle" -> {
                val d = radius * 0.55f
                val tri = Path().apply {
                    moveTo(center.x, center.y - d)
                    lineTo(center.x + d, center.y + d * 0.8f)
                    lineTo(center.x - d, center.y + d * 0.8f)
                    close()
                }
                drawPath(tri, if (isPressed) Color.White else brandColor, style = Stroke(width = 2.5f))
            }
        }
    }

    // Triangle (North)
    drawBtn(Offset(cx, cy - spacing), input.buttonTriangle, PSTriangleGreen, "triangle")
    // Circle (East)
    drawBtn(Offset(cx + spacing, cy), input.buttonCircle, PSCircleRed, "circle")
    // Cross (South)
    drawBtn(Offset(cx, cy + spacing), input.buttonCross, PSCrossBlue, "cross")
    // Square (West)
    drawBtn(Offset(cx - spacing, cy), input.buttonSquare, PSSquarePink, "square")
}

private fun DrawScope.drawShoulderButtons(w: Float, h: Float, input: InputState, activeColor: Color) {
    // L1 & R1
    val l1Active = input.buttonL1
    val r1Active = input.buttonR1

    // L1
    drawRoundRect(
        color = if (l1Active) activeColor else Color.Gray.copy(alpha = 0.3f),
        topLeft = Offset(w * 0.18f, h * 0.08f),
        size = Size(w * 0.12f, h * 0.05f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // R1
    drawRoundRect(
        color = if (r1Active) activeColor else Color.Gray.copy(alpha = 0.3f),
        topLeft = Offset(w * 0.70f, h * 0.08f),
        size = Size(w * 0.12f, h * 0.05f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // L2 & R2 Triggers with analog pressure gauges
    val l2Pressure = input.l2Pressure.coerceIn(0f, 1f)
    val r2Pressure = input.r2Pressure.coerceIn(0f, 1f)

    // L2 container
    drawRoundRect(
        color = Color.DarkGray.copy(alpha = 0.4f),
        topLeft = Offset(w * 0.18f, h * 0.02f),
        size = Size(w * 0.12f, h * 0.045f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    if (l2Pressure > 0f || input.buttonL2) {
        val fillW = w * 0.12f * (if (l2Pressure > 0f) l2Pressure else 1f)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(w * 0.18f, h * 0.02f),
            size = Size(fillW, h * 0.045f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }

    // R2 container
    drawRoundRect(
        color = Color.DarkGray.copy(alpha = 0.4f),
        topLeft = Offset(w * 0.70f, h * 0.02f),
        size = Size(w * 0.12f, h * 0.045f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    if (r2Pressure > 0f || input.buttonR2) {
        val fillW = w * 0.12f * (if (r2Pressure > 0f) r2Pressure else 1f)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(w * 0.70f, h * 0.02f),
            size = Size(fillW, h * 0.045f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

private fun DrawScope.drawAnalogSticks(w: Float, h: Float, input: InputState, outlineColor: Color, activeColor: Color) {
    val stickBaseRadius = w * 0.065f
    val stickCapRadius = w * 0.045f
    val maxDeflection = w * 0.022f

    fun drawStick(baseCenter: Offset, normX: Float, normY: Float, isClicked: Boolean) {
        // Base ring / well
        drawCircle(
            color = outlineColor.copy(alpha = 0.25f),
            radius = stickBaseRadius,
            center = baseCenter
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.6f),
            radius = stickBaseRadius,
            center = baseCenter,
            style = Stroke(width = 2f)
        )

        // Center crosshair / deadzone circle
        drawCircle(
            color = outlineColor.copy(alpha = 0.3f),
            radius = stickBaseRadius * 0.35f,
            center = baseCenter,
            style = Stroke(width = 1f)
        )

        // Deflected cap center
        val capX = baseCenter.x + (normX * maxDeflection).coerceIn(-maxDeflection, maxDeflection)
        val capY = baseCenter.y + (normY * maxDeflection).coerceIn(-maxDeflection, maxDeflection)
        val capCenter = Offset(capX, capY)

        // Deflection indicator vector line
        if (normX != 0f || normY != 0f) {
            drawLine(
                color = activeColor.copy(alpha = 0.7f),
                start = baseCenter,
                end = capCenter,
                strokeWidth = 2f
            )
        }

        // Stick cap
        val capColor = if (isClicked) activeColor else Color(0xFF263238)
        drawCircle(color = capColor, radius = stickCapRadius, center = capCenter)
        drawCircle(
            color = if (isClicked) Color.White else outlineColor,
            radius = stickCapRadius,
            center = capCenter,
            style = Stroke(width = 2f)
        )
        // Inner grip ring
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = stickCapRadius * 0.6f,
            center = capCenter,
            style = Stroke(width = 1.5f)
        )
    }

    // Left Stick
    drawStick(
        baseCenter = Offset(w * 0.36f, h * 0.58f),
        normX = input.leftStickX,
        normY = input.leftStickY,
        isClicked = input.buttonL3
    )

    // Right Stick
    drawStick(
        baseCenter = Offset(w * 0.64f, h * 0.58f),
        normX = input.rightStickX,
        normY = input.rightStickY,
        isClicked = input.buttonR3
    )
}

private fun DrawScope.drawCenterButtons(w: Float, h: Float, input: InputState, outlineColor: Color, activeColor: Color) {
    // Share button
    val shareCenter = Offset(w * 0.34f, h * 0.28f)
    val shareFill = if (input.buttonShare) activeColor else outlineColor.copy(alpha = 0.3f)
    drawRoundRect(
        color = shareFill,
        topLeft = Offset(shareCenter.x - w * 0.012f, shareCenter.y - h * 0.035f),
        size = Size(w * 0.024f, h * 0.07f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // Options button
    val optionsCenter = Offset(w * 0.66f, h * 0.28f)
    val optionsFill = if (input.buttonOptions) activeColor else outlineColor.copy(alpha = 0.3f)
    drawRoundRect(
        color = optionsFill,
        topLeft = Offset(optionsCenter.x - w * 0.012f, optionsCenter.y - h * 0.035f),
        size = Size(w * 0.024f, h * 0.07f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // PS Home Button
    val psCenter = Offset(w * 0.50f, h * 0.58f)
    val psRadius = w * 0.025f
    val psFill = if (input.buttonPs) activeColor else Color(0xFF1E293B)
    drawCircle(color = psFill, radius = psRadius, center = psCenter)
    drawCircle(color = outlineColor, radius = psRadius, center = psCenter, style = Stroke(width = 2f))
}
