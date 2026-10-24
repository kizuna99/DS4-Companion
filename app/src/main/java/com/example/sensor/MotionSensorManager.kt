package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.InputDevice
import com.example.model.MotionSensorsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Handles DualShock 4 6-axis IMU (3-axis Accelerometer + 3-axis Gyroscope).
 *
 * Implements:
 * 1. Gravity estimation and dynamic linear acceleration decoupling.
 * 2. Stationary detection window to prevent resting noise from triggering false movement.
 * 3. Gyroscope bias estimation and zero-deadzone clamping while stationary.
 * 4. Android 12+ (API 31+) InputDevice.sensorManager for physical DualShock 4 hardware.
 * 5. Simulation engine when simulation mode is active.
 */
class MotionSensorManager(private val context: Context) {

    private val TAG = "MotionSensorManager"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _motionSensors = MutableStateFlow(MotionSensorsState())
    val motionSensors: StateFlow<MotionSensorsState> = _motionSensors.asStateFlow()

    private var activeDeviceSensorManager: SensorManager? = null
    private var simulationJob: Job? = null

    // Gravity vector low-pass estimation
    private var gravityX = 0f
    private var gravityY = 9.8f
    private var gravityZ = 0f
    private var hasGravityInit = false

    // Gyroscope stationary bias estimation
    private var gyroBiasX = 0f
    private var gyroBiasY = 0f
    private var gyroBiasZ = 0f
    private var stationarySampleCount = 0
    private var isCalibrated = false

    // Latest telemetry
    private var latestRawAccelX = 0f
    private var latestRawAccelY = 9.8f
    private var latestRawAccelZ = 0f
    private var latestRawGyroX = 0f
    private var latestRawGyroY = 0f
    private var latestRawGyroZ = 0f
    private var latestDynamicAccel = 0f
    private var latestAngularSpeed = 0f

    companion object {
        const val GYRO_NOISE_DEADZONE = 0.025f       // rad/s (~1.4 deg/s)
        const val ACCEL_NOISE_DEADZONE = 0.15f      // m/s²
        const val DYNAMIC_ACCEL_THRESHOLD = 0.32f   // m/s² dynamic movement threshold
        const val ANGULAR_SPEED_THRESHOLD = 0.075f  // rad/s angular motion threshold
        const val STATIONARY_SAMPLES_REQUIRED = 8   // samples before locking bias
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            val values = event.values ?: return

            when (event.sensor?.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (values.size >= 3) {
                        val rawX = values[0]
                        val rawY = values[1]
                        val rawZ = values[2]
                        latestRawAccelX = rawX
                        latestRawAccelY = rawY
                        latestRawAccelZ = rawZ

                        if (!hasGravityInit) {
                            gravityX = rawX
                            gravityY = rawY
                            gravityZ = rawZ
                            hasGravityInit = true
                        } else {
                            // Low-pass filter to estimate gravitational vector
                            val alpha = 0.85f
                            gravityX = alpha * gravityX + (1f - alpha) * rawX
                            gravityY = alpha * gravityY + (1f - alpha) * rawY
                            gravityZ = alpha * gravityZ + (1f - alpha) * rawZ
                        }

                        val dynX = rawX - gravityX
                        val dynY = rawY - gravityY
                        val dynZ = rawZ - gravityZ
                        latestDynamicAccel = sqrt(dynX * dynX + dynY * dynY + dynZ * dynZ)
                        updateMotionState()
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (values.size >= 3) {
                        val rawX = values[0]
                        val rawY = values[1]
                        val rawZ = values[2]
                        latestRawGyroX = rawX
                        latestRawGyroY = rawY
                        latestRawGyroZ = rawZ
                        latestAngularSpeed = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
                        updateMotionState()
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateMotionState() {
        val isStationaryNow = latestDynamicAccel < DYNAMIC_ACCEL_THRESHOLD && latestAngularSpeed < ANGULAR_SPEED_THRESHOLD

        if (isStationaryNow) {
            stationarySampleCount++
            if (stationarySampleCount >= STATIONARY_SAMPLES_REQUIRED) {
                // Adaptively estimate and cancel residual stationary gyro bias
                val biasAlpha = 0.95f
                gyroBiasX = biasAlpha * gyroBiasX + (1f - biasAlpha) * latestRawGyroX
                gyroBiasY = biasAlpha * gyroBiasY + (1f - biasAlpha) * latestRawGyroY
                gyroBiasZ = biasAlpha * gyroBiasZ + (1f - biasAlpha) * latestRawGyroZ
                isCalibrated = true
            }
        } else {
            stationarySampleCount = 0
        }

        // Calibrate raw gyroscope by removing estimated stationary bias
        val calGyroX = latestRawGyroX - gyroBiasX
        val calGyroY = latestRawGyroY - gyroBiasY
        val calGyroZ = latestRawGyroZ - gyroBiasZ

        // Apply deadzone to suppress baseline MEMS noise when resting
        val filteredGyroX = if (abs(calGyroX) < GYRO_NOISE_DEADZONE) 0f else calGyroX
        val filteredGyroY = if (abs(calGyroY) < GYRO_NOISE_DEADZONE) 0f else calGyroY
        val filteredGyroZ = if (abs(calGyroZ) < GYRO_NOISE_DEADZONE) 0f else calGyroZ

        // Filter small accelerometer noise on static gravity vector
        val dynX = latestRawAccelX - gravityX
        val dynY = latestRawAccelY - gravityY
        val dynZ = latestRawAccelZ - gravityZ

        val filteredAccelX = if (abs(dynX) < ACCEL_NOISE_DEADZONE) gravityX else (gravityX + dynX)
        val filteredAccelY = if (abs(dynY) < ACCEL_NOISE_DEADZONE) gravityY else (gravityY + dynY)
        val filteredAccelZ = if (abs(dynZ) < ACCEL_NOISE_DEADZONE) gravityZ else (gravityZ + dynZ)

        val isMoving = !isStationaryNow || (filteredGyroX != 0f || filteredGyroY != 0f || filteredGyroZ != 0f)

        val statusText = when {
            !isCalibrated -> "Calibrating Stationary Noise..."
            isMoving -> "Active Movement Detected"
            else -> "Stationary (Noise Filtered)"
        }

        _motionSensors.value = MotionSensorsState(
            accelX = filteredAccelX,
            accelY = filteredAccelY,
            accelZ = filteredAccelZ,
            gyroX = filteredGyroX,
            gyroY = filteredGyroY,
            gyroZ = filteredGyroZ,
            isMoving = isMoving,
            isStationary = !isMoving,
            dynamicAcceleration = latestDynamicAccel,
            angularSpeed = latestAngularSpeed,
            isCalibrated = isCalibrated,
            isAvailable = true,
            sensorStatus = statusText
        )
    }

    fun resetCalibration() {
        gravityX = 0f
        gravityY = 9.8f
        gravityZ = 0f
        hasGravityInit = false
        gyroBiasX = 0f
        gyroBiasY = 0f
        gyroBiasZ = 0f
        stationarySampleCount = 0
        isCalibrated = false
        latestDynamicAccel = 0f
        latestAngularSpeed = 0f
        latestRawGyroX = 0f
        latestRawGyroY = 0f
        latestRawGyroZ = 0f
    }

    fun bindController(inputDeviceId: Int?) {
        unbindController()
        if (inputDeviceId == null) return

        resetCalibration()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = InputDevice.getDevice(inputDeviceId) ?: return
            try {
                val sm = device.sensorManager
                if (sm != null) {
                    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

                    if (accel != null) {
                        sm.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_GAME)
                    }
                    if (gyro != null) {
                        sm.registerListener(sensorListener, gyro, SensorManager.SENSOR_DELAY_GAME)
                    }

                    if (accel != null || gyro != null) {
                        activeDeviceSensorManager = sm
                        _motionSensors.value = MotionSensorsState(
                            isAvailable = true,
                            sensorStatus = "Connected (Hardware 6-Axis)"
                        )
                        Log.i(TAG, "Bound controller sensor manager for device $inputDeviceId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error binding controller sensor manager", e)
            }
        }
    }

    fun unbindController() {
        try {
            activeDeviceSensorManager?.unregisterListener(sensorListener)
            activeDeviceSensorManager = null
            resetCalibration()
            _motionSensors.value = MotionSensorsState(
                isAvailable = false,
                sensorStatus = "Ready"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding controller sensor manager", e)
        }
    }

    fun startSimulation() {
        stopSimulation()
        resetCalibration()
        _motionSensors.value = MotionSensorsState(
            accelX = 0f,
            accelY = 9.8f,
            accelZ = 0f,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f,
            isMoving = false,
            isStationary = true,
            dynamicAcceleration = 0f,
            angularSpeed = 0f,
            isCalibrated = true,
            isAvailable = true,
            sensorStatus = "Stationary (Resting on Surface)"
        )
        simulationJob = scope.launch {
            var step = 0f
            while (isActive) {
                step += 0.05f
                // In simulation, simulate alternating between stationary resting (2 seconds) and gentle movement (2 seconds)
                val isSimStationary = (step % 8f) < 4f

                if (isSimStationary) {
                    _motionSensors.value = MotionSensorsState(
                        accelX = 0f,
                        accelY = 9.8f,
                        accelZ = 0f,
                        gyroX = 0f,
                        gyroY = 0f,
                        gyroZ = 0f,
                        isMoving = false,
                        isStationary = true,
                        dynamicAcceleration = 0f,
                        angularSpeed = 0f,
                        isCalibrated = true,
                        isAvailable = true,
                        sensorStatus = "Stationary (Resting on Surface)"
                    )
                } else {
                    val accelX = sin(step) * 1.2f
                    val accelY = 9.8f + cos(step * 0.7f) * 0.6f
                    val accelZ = sin(step * 0.5f) * 0.4f

                    val gyroX = cos(step) * 0.15f
                    val gyroY = sin(step * 0.8f) * 0.18f
                    val gyroZ = cos(step * 1.2f) * 0.10f

                    _motionSensors.value = MotionSensorsState(
                        accelX = accelX,
                        accelY = accelY,
                        accelZ = accelZ,
                        gyroX = gyroX,
                        gyroY = gyroY,
                        gyroZ = gyroZ,
                        isMoving = true,
                        isStationary = false,
                        dynamicAcceleration = 1.3f,
                        angularSpeed = 0.22f,
                        isCalibrated = true,
                        isAvailable = true,
                        sensorStatus = "Active Movement (Simulation)"
                    )
                }
                delay(30)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    fun release() {
        unbindController()
        stopSimulation()
    }
}
