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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.InputState
import com.example.model.LightBarColor
import com.example.ui.theme.PSCircleRed
import com.example.ui.theme.PSCrossBlue
import com.example.ui.theme.PSSquarePink
import com.example.ui.theme.PSTriangleGreen

/**
 * A clean, highly accurate front-facing Sony DualShock 4 controller vector illustration.
 * Features realistic proportions, symmetrical silhouette, D-pad, action buttons,
 * thumbsticks with deflection, touchpad with touch & click indicator, speaker grille,
 * PS button, Share/Options pills, L1/R1 bumpers, L2/R2 triggers, and customizable RGB light bar.
 * Contains NO technical annotations, callouts, or external arrows.
 */
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
            .aspectRatio(1.58f)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // 1. Draw L2 and R2 Triggers (situated behind shoulders)
            drawTriggerTrays(w, h, inputState, activeAccentColor)

            // 2. Draw Controller Main Outer Shell (Handles & Center Body)
            drawControllerBody(w, h, surfaceColor, outlineColor)

            // 3. Draw Glowing RGB Light Bar & V2 Touchpad Light Strip
            drawLightBar(w, h, animatedLightColor)

            // 4. Draw Touchpad Plate with touch position & click glow
            drawTouchpad(w, h, inputState, outlineColor, activeAccentColor, animatedLightColor)

            // 5. Draw Speaker Grille Perforations
            drawSpeakerGrille(w, h)

            // 6. Draw PS Button with iconic monogram
            drawPSButton(w, h, inputState, outlineColor, activeAccentColor)

            // 7. Draw Stereo Headset / EXT Port Notch
            drawHeadsetExtPort(w, h)

            // 8. Draw Share & Options Angled Pill Buttons
            drawShareAndOptions(w, h, inputState, outlineColor, activeAccentColor)

            // 9. Draw D-Pad with Embossed Directional Arrows
            drawDPad(w, h, inputState, outlineColor, activeAccentColor)

            // 10. Draw Action Buttons (Triangle, Circle, Cross, Square)
            drawActionButtons(w, h, inputState, outlineColor)

            // 11. Draw L1 and R1 Shoulder Bumpers
            drawShoulderBumpers(w, h, inputState, activeAccentColor)

            // 12. Draw Detailed Dual Analog Sticks with Deflection
            drawAnalogSticks(w, h, inputState, outlineColor, activeAccentColor)
        }
    }
}

/**
 * Draws L2 and R2 triggers peeking up behind the top shoulders of the controller.
 */
private fun DrawScope.drawTriggerTrays(
    w: Float,
    h: Float,
    input: InputState,
    activeColor: Color
) {
    val l2Pressure = input.l2Pressure.coerceIn(0f, 1f)
    val r2Pressure = input.r2Pressure.coerceIn(0f, 1f)
    val isL2Active = input.buttonL2 || l2Pressure > 0.1f
    val isR2Active = input.buttonR2 || r2Pressure > 0.1f

    val triggerWidth = w * 0.115f
    val triggerHeight = h * 0.12f

    // L2 Trigger
    val l2Left = w * 0.165f
    val l2Top = h * 0.035f
    val l2Fill = if (isL2Active) activeColor else Color(0xFF1E2530)
    drawRoundRect(
        color = l2Fill,
        topLeft = Offset(l2Left, l2Top),
        size = Size(triggerWidth, triggerHeight),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = if (isL2Active) Color.White.copy(alpha = 0.8f) else Color(0xFF333E50),
        topLeft = Offset(l2Left, l2Top),
        size = Size(triggerWidth, triggerHeight),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = if (isL2Active) 2.5f else 1.5f)
    )

    // R2 Trigger
    val r2Left = w * 0.720f
    val r2Top = h * 0.035f
    val r2Fill = if (isR2Active) activeColor else Color(0xFF1E2530)
    drawRoundRect(
        color = r2Fill,
        topLeft = Offset(r2Left, r2Top),
        size = Size(triggerWidth, triggerHeight),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = if (isR2Active) Color.White.copy(alpha = 0.8f) else Color(0xFF333E50),
        topLeft = Offset(r2Left, r2Top),
        size = Size(triggerWidth, triggerHeight),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = if (isR2Active) 2.5f else 1.5f)
    )
}

/**
 * Draws the accurate symmetrical outer shell of the DualShock 4 controller.
 */
private fun DrawScope.drawControllerBody(
    w: Float,
    h: Float,
    surfaceColor: Color,
    outlineColor: Color
) {
    val bodyPath = Path().apply {
        // Start at left shoulder inner corner
        moveTo(w * 0.35f, h * 0.14f)
        // Top dip for light bar
        lineTo(w * 0.65f, h * 0.14f)
        // Right shoulder top
        lineTo(w * 0.82f, h * 0.14f)
        // Right shoulder outer curve
        cubicTo(w * 0.89f, h * 0.16f, w * 0.955f, h * 0.30f, w * 0.945f, h * 0.50f)
        // Right grip outer swell
        cubicTo(w * 0.940f, h * 0.68f, w * 0.875f, h * 0.94f, w * 0.805f, h * 0.95f)
        // Right grip bottom tip
        cubicTo(w * 0.755f, h * 0.96f, w * 0.725f, h * 0.86f, w * 0.685f, h * 0.73f)
        // Right inner curve to bridge
        cubicTo(w * 0.640f, h * 0.66f, w * 0.585f, h * 0.63f, w * 0.500f, h * 0.63f)
        // Left inner curve from bridge
        cubicTo(w * 0.415f, h * 0.63f, w * 0.360f, h * 0.66f, w * 0.315f, h * 0.73f)
        // Left grip inner curve
        cubicTo(w * 0.275f, h * 0.86f, w * 0.245f, h * 0.96f, w * 0.195f, h * 0.95f)
        // Left grip outer swell
        cubicTo(w * 0.125f, h * 0.94f, w * 0.060f, h * 0.68f, w * 0.055f, h * 0.50f)
        // Left shoulder outer curve
        cubicTo(w * 0.045f, h * 0.30f, w * 0.11f, h * 0.16f, w * 0.18f, h * 0.14f)
        close()
    }

    // Body base fill with 3D depth gradient
    val bodyGradient = Brush.verticalGradient(
        colors = listOf(
            surfaceColor,
            Color(0xFF131924),
            Color(0xFF0F141E)
        ),
        startY = h * 0.14f,
        endY = h * 0.96f
    )
    drawPath(bodyPath, brush = bodyGradient, style = Fill)

    // Outer contour edge stroke
    drawPath(
        path = bodyPath,
        color = outlineColor.copy(alpha = 0.55f),
        style = Stroke(width = 3f, join = StrokeJoin.Round)
    )

    // Grip texture seam lines (subtle ergonomic split characteristic of DS4)
    val leftSeam = Path().apply {
        moveTo(w * 0.13f, h * 0.38f)
        cubicTo(w * 0.16f, h * 0.58f, w * 0.22f, h * 0.82f, w * 0.25f, h * 0.92f)
    }
    drawPath(leftSeam, color = outlineColor.copy(alpha = 0.22f), style = Stroke(width = 1.5f))

    val rightSeam = Path().apply {
        moveTo(w * 0.87f, h * 0.38f)
        cubicTo(w * 0.84f, h * 0.58f, w * 0.78f, h * 0.82f, w * 0.75f, h * 0.92f)
    }
    drawPath(rightSeam, color = outlineColor.copy(alpha = 0.22f), style = Stroke(width = 1.5f))
}

/**
 * Draws the RGB Light Bar with radiant illumination.
 */
private fun DrawScope.drawLightBar(w: Float, h: Float, color: Color) {
    val barWidth = w * 0.28f
    val barHeight = h * 0.045f
    val left = (w - barWidth) / 2f
    val top = h * 0.115f

    // Outer luminous bloom / radial glow
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(w * 0.5f, top + barHeight / 2f),
            radius = barWidth * 0.75f
        ),
        topLeft = Offset(left - 24f, top - 16f),
        size = Size(barWidth + 48f, barHeight + 32f),
        cornerRadius = CornerRadius(20f, 20f)
    )

    // Solid core light bar lens
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Specular highlight line on top edge of light bar
    drawRoundRect(
        color = Color.White.copy(alpha = 0.6f),
        topLeft = Offset(left + 8f, top + 2f),
        size = Size(barWidth - 16f, barHeight * 0.35f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

/**
 * Draws the large center capacitive touchpad with touch coordinates and click glow.
 */
private fun DrawScope.drawTouchpad(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color,
    activeColor: Color,
    lightBarColor: Color
) {
    val padWidth = w * 0.28f
    val padHeight = h * 0.25f
    val left = (w - padWidth) / 2f
    val top = h * 0.165f

    val isClicked = input.buttonTouchpad || input.touchpad.isClicked

    // Touchpad background plate
    val padFill = if (isClicked) {
        activeColor.copy(alpha = 0.35f)
    } else {
        Color(0xFF161C26)
    }

    drawRoundRect(
        color = padFill,
        topLeft = Offset(left, top),
        size = Size(padWidth, padHeight),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Touchpad border
    drawRoundRect(
        color = if (isClicked) activeColor else outlineColor.copy(alpha = 0.45f),
        topLeft = Offset(left, top),
        size = Size(padWidth, padHeight),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = if (isClicked) 3.5f else 1.5f)
    )

    // DS4 V2 Slim Light Strip at top edge of touchpad
    val stripWidth = padWidth * 0.65f
    val stripLeft = (w - stripWidth) / 2f
    val stripTop = top + 3f
    drawRoundRect(
        color = lightBarColor.copy(alpha = 0.90f),
        topLeft = Offset(stripLeft, stripTop),
        size = Size(stripWidth, 4f),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Live Touch Indicator Dot (when touching the surface)
    if (input.touchpad.isTouching) {
        val touchPx = left + (padWidth * input.touchpad.x.coerceIn(0.06f, 0.94f))
        val touchPy = top + (padHeight * input.touchpad.y.coerceIn(0.08f, 0.92f))
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.35f),
            radius = 12f,
            center = Offset(touchPx, touchPy)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 6f,
            center = Offset(touchPx, touchPy)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(touchPx, touchPy)
        )
    }
}

/**
 * Draws the cluster of speaker grille perforations between the touchpad and PS button.
 */
private fun DrawScope.drawSpeakerGrille(w: Float, h: Float) {
    val cx = w * 0.50f
    val cy = h * 0.475f
    val holeColor = Color(0xFF0A0E14)
    val dotRadius = 1.6f
    val spacingX = w * 0.011f
    val spacingY = h * 0.016f

    // 4 rows of subtle speaker holes
    val rows = listOf(3, 5, 5, 3)
    rows.forEachIndexed { rowIndex, count ->
        val y = cy + (rowIndex - 1.5f) * spacingY
        val startX = cx - ((count - 1) * spacingX) / 2f
        for (i in 0 until count) {
            val x = startX + i * spacingX
            drawCircle(color = holeColor, radius = dotRadius, center = Offset(x, y))
        }
    }
}

/**
 * Draws the circular PS button with stylized PlayStation monogram.
 */
private fun DrawScope.drawPSButton(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color,
    activeColor: Color
) {
    val center = Offset(w * 0.50f, h * 0.555f)
    val radius = w * 0.026f
    val isPressed = input.buttonPs

    // Base circle
    val fill = if (isPressed) activeColor else Color(0xFF141922)
    drawCircle(color = fill, radius = radius, center = center)
    drawCircle(
        color = if (isPressed) Color.White else outlineColor.copy(alpha = 0.6f),
        radius = radius,
        center = center,
        style = Stroke(width = if (isPressed) 2.5f else 1.5f)
    )

    // PlayStation stylized 'PS' mark inside
    val markColor = if (isPressed) Color.White else Color(0xFFCFD8DC)
    // Upright 'P' stem
    drawLine(
        color = markColor,
        start = Offset(center.x - radius * 0.15f, center.y + radius * 0.5f),
        end = Offset(center.x - radius * 0.15f, center.y - radius * 0.5f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
    // 'P' loop
    drawArc(
        color = markColor,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.25f, center.y - radius * 0.5f),
        size = Size(radius * 0.65f, radius * 0.55f),
        style = Stroke(width = 2f)
    )
    // 'S' horizontal shadow line
    drawLine(
        color = markColor,
        start = Offset(center.x - radius * 0.45f, center.y + radius * 0.35f),
        end = Offset(center.x + radius * 0.45f, center.y + radius * 0.35f),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )
}

/**
 * Draws the stereo headset / EXT port recess notch on the bottom edge.
 */
private fun DrawScope.drawHeadsetExtPort(w: Float, h: Float) {
    val portWidth = w * 0.045f
    val portHeight = h * 0.012f
    val left = (w - portWidth) / 2f
    val top = h * 0.625f

    drawRoundRect(
        color = Color(0xFF090C10),
        topLeft = Offset(left, top),
        size = Size(portWidth, portHeight),
        cornerRadius = CornerRadius(2f, 2f)
    )
}

/**
 * Draws Share and Options angled pill buttons beside the touchpad.
 */
private fun DrawScope.drawShareAndOptions(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color,
    activeColor: Color
) {
    val btnWidth = w * 0.022f
    val btnHeight = h * 0.065f

    // Share button (Left)
    val shareCenter = Offset(w * 0.315f, h * 0.245f)
    val isSharePressed = input.buttonShare
    val shareFill = if (isSharePressed) activeColor else Color(0xFF1E2530)

    drawRoundRect(
        color = shareFill,
        topLeft = Offset(shareCenter.x - btnWidth / 2f, shareCenter.y - btnHeight / 2f),
        size = Size(btnWidth, btnHeight),
        cornerRadius = CornerRadius(btnWidth / 2f, btnWidth / 2f)
    )
    drawRoundRect(
        color = if (isSharePressed) Color.White else outlineColor.copy(alpha = 0.5f),
        topLeft = Offset(shareCenter.x - btnWidth / 2f, shareCenter.y - btnHeight / 2f),
        size = Size(btnWidth, btnHeight),
        cornerRadius = CornerRadius(btnWidth / 2f, btnWidth / 2f),
        style = Stroke(width = if (isSharePressed) 2f else 1f)
    )

    // Options button (Right)
    val optionsCenter = Offset(w * 0.685f, h * 0.245f)
    val isOptionsPressed = input.buttonOptions
    val optionsFill = if (isOptionsPressed) activeColor else Color(0xFF1E2530)

    drawRoundRect(
        color = optionsFill,
        topLeft = Offset(optionsCenter.x - btnWidth / 2f, optionsCenter.y - btnHeight / 2f),
        size = Size(btnWidth, btnHeight),
        cornerRadius = CornerRadius(btnWidth / 2f, btnWidth / 2f)
    )
    drawRoundRect(
        color = if (isOptionsPressed) Color.White else outlineColor.copy(alpha = 0.5f),
        topLeft = Offset(optionsCenter.x - btnWidth / 2f, optionsCenter.y - btnHeight / 2f),
        size = Size(btnWidth, btnHeight),
        cornerRadius = CornerRadius(btnWidth / 2f, btnWidth / 2f),
        style = Stroke(width = if (isOptionsPressed) 2f else 1f)
    )
}

/**
 * Draws the D-Pad with 4 independent directional buttons with embossed arrows.
 */
private fun DrawScope.drawDPad(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color,
    activeColor: Color
) {
    val cx = w * 0.225f
    val cy = h * 0.405f
    val armLength = w * 0.046f
    val armWidth = w * 0.038f

    // Recessed circular dish under D-pad
    drawCircle(
        color = Color(0xFF0F141E),
        radius = w * 0.072f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = outlineColor.copy(alpha = 0.25f),
        radius = w * 0.072f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )

    // Helper for each directional button
    fun drawDirectionArm(
        topLeft: Offset,
        isPressed: Boolean,
        isHorizontal: Boolean,
        dir: String
    ) {
        val size = if (isHorizontal) Size(armLength, armWidth) else Size(armWidth, armLength)
        val fill = if (isPressed) activeColor else Color(0xFF1E2530)
        drawRoundRect(
            color = fill,
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = if (isPressed) Color.White else outlineColor.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = if (isPressed) 2.5f else 1.2f)
        )

        // Embossed arrow inside
        val arrowColor = if (isPressed) Color.White else Color(0xFF90A4AE)
        val center = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
        val arrowSize = w * 0.010f

        val arrowPath = Path().apply {
            when (dir) {
                "UP" -> {
                    moveTo(center.x, center.y - arrowSize)
                    lineTo(center.x + arrowSize * 0.9f, center.y + arrowSize * 0.6f)
                    lineTo(center.x - arrowSize * 0.9f, center.y + arrowSize * 0.6f)
                }
                "DOWN" -> {
                    moveTo(center.x, center.y + arrowSize)
                    lineTo(center.x + arrowSize * 0.9f, center.y - arrowSize * 0.6f)
                    lineTo(center.x - arrowSize * 0.9f, center.y - arrowSize * 0.6f)
                }
                "LEFT" -> {
                    moveTo(center.x - arrowSize, center.y)
                    lineTo(center.x + arrowSize * 0.6f, center.y - arrowSize * 0.9f)
                    lineTo(center.x + arrowSize * 0.6f, center.y + arrowSize * 0.9f)
                }
                "RIGHT" -> {
                    moveTo(center.x + arrowSize, center.y)
                    lineTo(center.x - arrowSize * 0.6f, center.y - arrowSize * 0.9f)
                    lineTo(center.x - arrowSize * 0.6f, center.y + arrowSize * 0.9f)
                }
            }
            close()
        }
        drawPath(arrowPath, color = arrowColor, style = Fill)
    }

    // Up
    drawDirectionArm(
        topLeft = Offset(cx - armWidth / 2f, cy - armLength - armWidth * 0.25f),
        isPressed = input.dpadUp,
        isHorizontal = false,
        dir = "UP"
    )
    // Down
    drawDirectionArm(
        topLeft = Offset(cx - armWidth / 2f, cy + armWidth * 0.25f),
        isPressed = input.dpadDown,
        isHorizontal = false,
        dir = "DOWN"
    )
    // Left
    drawDirectionArm(
        topLeft = Offset(cx - armLength - armWidth * 0.25f, cy - armWidth / 2f),
        isPressed = input.dpadLeft,
        isHorizontal = true,
        dir = "LEFT"
    )
    // Right
    drawDirectionArm(
        topLeft = Offset(cx + armWidth * 0.25f, cy - armWidth / 2f),
        isPressed = input.dpadRight,
        isHorizontal = true,
        dir = "RIGHT"
    )

    // Center circular pivot
    drawCircle(
        color = Color(0xFF141922),
        radius = armWidth * 0.65f,
        center = Offset(cx, cy)
    )
}

/**
 * Draws the authentic PlayStation action buttons (Triangle, Circle, Cross, Square).
 */
private fun DrawScope.drawActionButtons(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color
) {
    val cx = w * 0.775f
    val cy = h * 0.405f
    val spacing = w * 0.046f
    val radius = w * 0.023f

    // Recessed circular dish under action buttons
    drawCircle(
        color = Color(0xFF0F141E),
        radius = w * 0.072f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = outlineColor.copy(alpha = 0.25f),
        radius = w * 0.072f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )

    fun drawBtn(center: Offset, isPressed: Boolean, brandColor: Color, symbol: String) {
        val fill = if (isPressed) brandColor else Color(0xFF1E2530)
        drawCircle(color = fill, radius = if (isPressed) radius * 1.12f else radius, center = center)
        drawCircle(
            color = if (isPressed) Color.White else outlineColor.copy(alpha = 0.55f),
            radius = if (isPressed) radius * 1.12f else radius,
            center = center,
            style = Stroke(width = if (isPressed) 2.5f else 1.2f)
        )

        val symColor = if (isPressed) Color.White else brandColor

        when (symbol) {
            "cross" -> {
                val d = radius * 0.48f
                drawLine(
                    color = symColor,
                    start = Offset(center.x - d, center.y - d),
                    end = Offset(center.x + d, center.y + d),
                    strokeWidth = 2.8f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = symColor,
                    start = Offset(center.x + d, center.y - d),
                    end = Offset(center.x - d, center.y + d),
                    strokeWidth = 2.8f,
                    cap = StrokeCap.Round
                )
            }
            "circle" -> {
                drawCircle(
                    color = symColor,
                    radius = radius * 0.48f,
                    center = center,
                    style = Stroke(width = 2.8f)
                )
            }
            "square" -> {
                val d = radius * 0.44f
                drawRect(
                    color = symColor,
                    topLeft = Offset(center.x - d, center.y - d),
                    size = Size(d * 2, d * 2),
                    style = Stroke(width = 2.8f)
                )
            }
            "triangle" -> {
                val d = radius * 0.52f
                val tri = Path().apply {
                    moveTo(center.x, center.y - d)
                    lineTo(center.x + d * 0.95f, center.y + d * 0.72f)
                    lineTo(center.x - d * 0.95f, center.y + d * 0.72f)
                    close()
                }
                drawPath(tri, color = symColor, style = Stroke(width = 2.8f, join = StrokeJoin.Round))
            }
        }
    }

    // Triangle (North, Green)
    drawBtn(Offset(cx, cy - spacing), input.buttonTriangle, PSTriangleGreen, "triangle")
    // Circle (East, Red)
    drawBtn(Offset(cx + spacing, cy), input.buttonCircle, PSCircleRed, "circle")
    // Cross (South, Blue)
    drawBtn(Offset(cx, cy + spacing), input.buttonCross, PSCrossBlue, "cross")
    // Square (West, Pink)
    drawBtn(Offset(cx - spacing, cy), input.buttonSquare, PSSquarePink, "square")
}

/**
 * Draws L1 and R1 shoulder bumpers.
 */
private fun DrawScope.drawShoulderBumpers(
    w: Float,
    h: Float,
    input: InputState,
    activeColor: Color
) {
    val bWidth = w * 0.115f
    val bHeight = h * 0.048f

    // L1 Bumper
    val l1Left = w * 0.165f
    val l1Top = h * 0.100f
    val isL1Pressed = input.buttonL1
    val l1Fill = if (isL1Pressed) activeColor else Color(0xFF252D3A)

    drawRoundRect(
        color = l1Fill,
        topLeft = Offset(l1Left, l1Top),
        size = Size(bWidth, bHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = if (isL1Pressed) Color.White else Color(0xFF3E4A5E),
        topLeft = Offset(l1Left, l1Top),
        size = Size(bWidth, bHeight),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = if (isL1Pressed) 2.5f else 1.2f)
    )

    // R1 Bumper
    val r1Left = w * 0.720f
    val r1Top = h * 0.100f
    val isR1Pressed = input.buttonR1
    val r1Fill = if (isR1Pressed) activeColor else Color(0xFF252D3A)

    drawRoundRect(
        color = r1Fill,
        topLeft = Offset(r1Left, r1Top),
        size = Size(bWidth, bHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = if (isR1Pressed) Color.White else Color(0xFF3E4A5E),
        topLeft = Offset(r1Left, r1Top),
        size = Size(bWidth, bHeight),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = if (isR1Pressed) 2.5f else 1.2f)
    )
}

/**
 * Draws detailed dual analog sticks with concentric rings, dished caps, and live deflection.
 */
private fun DrawScope.drawAnalogSticks(
    w: Float,
    h: Float,
    input: InputState,
    outlineColor: Color,
    activeColor: Color
) {
    val stickWellRadius = w * 0.075f
    val stickCapRadius = w * 0.052f
    val maxDeflection = w * 0.024f

    fun drawStick(
        baseCenter: Offset,
        normX: Float,
        normY: Float,
        isClicked: Boolean
    ) {
        // Deep circular base dish well
        drawCircle(
            color = Color(0xFF0C1017),
            radius = stickWellRadius,
            center = baseCenter
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.35f),
            radius = stickWellRadius,
            center = baseCenter,
            style = Stroke(width = 2f)
        )

        // Deflection vector calculation
        val capX = baseCenter.x + (normX * maxDeflection).coerceIn(-maxDeflection, maxDeflection)
        val capY = baseCenter.y + (normY * maxDeflection).coerceIn(-maxDeflection, maxDeflection)
        val capCenter = Offset(capX, capY)

        // Stick shaft / connection indicator if deflected
        if (normX != 0f || normY != 0f) {
            drawLine(
                color = activeColor.copy(alpha = 0.6f),
                start = baseCenter,
                end = capCenter,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // Stick outer rubber cap
        val capFill = if (isClicked) activeColor else Color(0xFF232A36)
        drawCircle(color = capFill, radius = stickCapRadius, center = capCenter)
        drawCircle(
            color = if (isClicked) Color.White else outlineColor.copy(alpha = 0.7f),
            radius = stickCapRadius,
            center = capCenter,
            style = Stroke(width = if (isClicked) 2.5f else 1.5f)
        )

        // Concentric outer traction ring
        drawCircle(
            color = if (isClicked) Color.White.copy(alpha = 0.7f) else Color(0xFF384355),
            radius = stickCapRadius * 0.82f,
            center = capCenter,
            style = Stroke(width = 1.8f)
        )

        // Inner concave thumb depression dish
        drawCircle(
            color = if (isClicked) activeColor.copy(alpha = 0.8f) else Color(0xFF161C26),
            radius = stickCapRadius * 0.60f,
            center = capCenter
        )

        // Center reticle dot
        drawCircle(
            color = if (isClicked) Color.White else Color(0xFF4A5568),
            radius = 2.5f,
            center = capCenter
        )
    }

    // Left Analog Stick (L3)
    drawStick(
        baseCenter = Offset(w * 0.360f, h * 0.620f),
        normX = input.leftStickX,
        normY = input.leftStickY,
        isClicked = input.buttonL3
    )

    // Right Analog Stick (R3)
    drawStick(
        baseCenter = Offset(w * 0.640f, h * 0.620f),
        normX = input.rightStickX,
        normY = input.rightStickY,
        isClicked = input.buttonR3
    )
}
