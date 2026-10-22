package com.example.model

data class InputLogEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val name: String,
    val action: String,
    val value: String
)

data class TouchpadState(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val isTouching: Boolean = false,
    val isClicked: Boolean = false,
    val touchCount: Int = 0
)

data class MotionSensorsState(
    val accelX: Float = 0f,
    val accelY: Float = 9.8f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val isAvailable: Boolean = false,
    val sensorStatus: String = "Ready"
)

data class InputState(
    // D-Pad
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,

    // Action Buttons
    val buttonCross: Boolean = false,
    val buttonCircle: Boolean = false,
    val buttonSquare: Boolean = false,
    val buttonTriangle: Boolean = false,

    // Shoulder Buttons
    val buttonL1: Boolean = false,
    val buttonR1: Boolean = false,
    val buttonL2: Boolean = false,
    val buttonR2: Boolean = false,

    // Analog Triggers (0.0 to 1.0)
    val l2Pressure: Float = 0f,
    val r2Pressure: Float = 0f,

    // Thumbsticks
    val buttonL3: Boolean = false,
    val buttonR3: Boolean = false,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,

    // Center / System buttons
    val buttonShare: Boolean = false,
    val buttonOptions: Boolean = false,
    val buttonPs: Boolean = false,
    val buttonTouchpad: Boolean = false,

    // Real-time Touchpad X/Y Telemetry
    val touchpad: TouchpadState = TouchpadState(),

    // Real-time IMU Motion Sensors (Accelerometer & Gyroscope)
    val motionSensors: MotionSensorsState = MotionSensorsState(),

    // Recent telemetry log (max 20 entries)
    val recentEvents: List<InputLogEvent> = emptyList(),

    // All distinct inputs touched in session
    val touchedInputs: Set<String> = emptySet(),

    // Timestamp of last update
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val anyButtonPressed: Boolean
        get() = dpadUp || dpadDown || dpadLeft || dpadRight ||
                buttonCross || buttonCircle || buttonSquare || buttonTriangle ||
                buttonL1 || buttonR1 || buttonL2 || buttonR2 ||
                buttonL3 || buttonR3 ||
                buttonShare || buttonOptions || buttonPs || buttonTouchpad || touchpad.isClicked

    val isLeftStickDeflected: Boolean
        get() = kotlin.math.abs(leftStickX) > 0.15f || kotlin.math.abs(leftStickY) > 0.15f

    val isRightStickDeflected: Boolean
        get() = kotlin.math.abs(rightStickX) > 0.15f || kotlin.math.abs(rightStickY) > 0.15f
}
