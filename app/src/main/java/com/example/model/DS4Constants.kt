package com.example.model

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

object DS4Constants {
    const val SONY_VENDOR_ID = 0x054C // 1356 decimal
    const val SONY_DS4_V1_PID = 0x05C4 // 1476 decimal (CUH-ZCT1)
    const val SONY_DS4_V2_PID = 0x09CC // 2508 decimal (CUH-ZCT2)
    const val SONY_DS4_DONGLE_PID = 0x0BA0 // 2976 decimal (Official Wireless Adapter)

    val KNOWN_DS4_PIDS = setOf(
        SONY_DS4_V1_PID,
        SONY_DS4_V2_PID,
        SONY_DS4_DONGLE_PID
    )

    fun isDualShock4(vendorId: Int, productId: Int, deviceName: String = ""): Boolean {
        if (vendorId == SONY_VENDOR_ID && productId in KNOWN_DS4_PIDS) return true
        val lower = deviceName.lowercase()
        return (lower.contains("wireless controller") && (vendorId == SONY_VENDOR_ID || vendorId == 0)) ||
                lower.contains("dualshock 4") ||
                lower.contains("sony interactive entertainment") ||
                (vendorId == SONY_VENDOR_ID && (lower.contains("controller") || lower.contains("touchpad"))) ||
                (lower.contains("touchpad") && (lower.contains("wireless") || lower.contains("sony") || lower.contains("dualshock")))
    }

    fun isGamepadOrJoystick(sources: Int): Boolean {
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    // PlayStation standard button mappings on Android
    const val KEY_CROSS = KeyEvent.KEYCODE_BUTTON_A
    const val KEY_CIRCLE = KeyEvent.KEYCODE_BUTTON_B
    const val KEY_SQUARE = KeyEvent.KEYCODE_BUTTON_X
    const val KEY_TRIANGLE = KeyEvent.KEYCODE_BUTTON_Y
    const val KEY_L1 = KeyEvent.KEYCODE_BUTTON_L1
    const val KEY_R1 = KeyEvent.KEYCODE_BUTTON_R1
    const val KEY_L2 = KeyEvent.KEYCODE_BUTTON_L2
    const val KEY_R2 = KeyEvent.KEYCODE_BUTTON_R2
    const val KEY_L3 = KeyEvent.KEYCODE_BUTTON_THUMBL
    const val KEY_R3 = KeyEvent.KEYCODE_BUTTON_THUMBR
    const val KEY_SHARE = KeyEvent.KEYCODE_BUTTON_SELECT
    const val KEY_OPTIONS = KeyEvent.KEYCODE_BUTTON_START
    const val KEY_PS = KeyEvent.KEYCODE_BUTTON_MODE
    const val KEY_TOUCHPAD = KeyEvent.KEYCODE_BUTTON_10
    const val KEY_BUTTON_TOUCH = 330 // Linux BTN_TOUCH
    const val KEY_BUTTON_1 = KeyEvent.KEYCODE_BUTTON_1

    fun isTouchpadKey(keyCode: Int): Boolean {
        return keyCode == KEY_TOUCHPAD ||
                keyCode == KEY_BUTTON_TOUCH ||
                keyCode == KEY_BUTTON_1 ||
                keyCode == KeyEvent.KEYCODE_BUTTON_C ||
                keyCode == KeyEvent.KEYCODE_BUTTON_Z
    }

    // Axes
    const val AXIS_LEFT_X = MotionEvent.AXIS_X
    const val AXIS_LEFT_Y = MotionEvent.AXIS_Y
    const val AXIS_RIGHT_X = MotionEvent.AXIS_Z
    const val AXIS_RIGHT_Y = MotionEvent.AXIS_RZ
    const val AXIS_L2_TRIGGER = MotionEvent.AXIS_LTRIGGER
    const val AXIS_L2_BRAKE = MotionEvent.AXIS_BRAKE
    const val AXIS_R2_TRIGGER = MotionEvent.AXIS_RTRIGGER
    const val AXIS_R2_GAS = MotionEvent.AXIS_GAS
    const val AXIS_HAT_X = MotionEvent.AXIS_HAT_X
    const val AXIS_HAT_Y = MotionEvent.AXIS_HAT_Y
}
