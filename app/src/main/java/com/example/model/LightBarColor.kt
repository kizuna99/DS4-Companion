package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

data class LightBarColor(
    val red: Int = 0,
    val green: Int = 55,
    val blue: Int = 145,
    val brightness: Float = 1.0f,
    val flashOn: Int = 0,
    val flashOff: Int = 0
) {
    val effectiveRed: Int
        get() = (red * brightness).roundToInt().coerceIn(0, 255)

    val effectiveGreen: Int
        get() = (green * brightness).roundToInt().coerceIn(0, 255)

    val effectiveBlue: Int
        get() = (blue * brightness).roundToInt().coerceIn(0, 255)

    fun toComposeColor(): Color {
        return Color(
            red = effectiveRed / 255f,
            green = effectiveGreen / 255f,
            blue = effectiveBlue / 255f,
            alpha = 1.0f
        )
    }

    fun toHex(): String {
        return String.format("#%02X%02X%02X", effectiveRed, effectiveGreen, effectiveBlue)
    }

    companion object {
        val PLAYSTATION_BLUE = LightBarColor(red = 0, green = 55, blue = 220)
        val COMBAT_RED = LightBarColor(red = 255, green = 20, blue = 20)
        val ACID_GREEN = LightBarColor(red = 0, green = 230, blue = 80)
        val NEON_PURPLE = LightBarColor(red = 170, green = 30, blue = 230)
        val ELECTRIC_CYAN = LightBarColor(red = 0, green = 220, blue = 255)
        val AMBER_YELLOW = LightBarColor(red = 255, green = 190, blue = 0)
        val PURE_WHITE = LightBarColor(red = 255, green = 255, blue = 255)
    }
}

data class LightBarPreset(
    val id: String,
    val name: String,
    val color: LightBarColor
)

val DEFAULT_PRESETS = listOf(
    LightBarPreset("ps_blue", "PlayStation Blue", LightBarColor.PLAYSTATION_BLUE),
    LightBarPreset("red", "Combat Red", LightBarColor.COMBAT_RED),
    LightBarPreset("green", "Acid Green", LightBarColor.ACID_GREEN),
    LightBarPreset("purple", "Neon Purple", LightBarColor.NEON_PURPLE),
    LightBarPreset("cyan", "Electric Cyan", LightBarColor.ELECTRIC_CYAN),
    LightBarPreset("yellow", "Amber Yellow", LightBarColor.AMBER_YELLOW),
    LightBarPreset("white", "Pure White", LightBarColor.PURE_WHITE)
)

data class LightBarApplyResult(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
