package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.InputDevice
import com.example.model.DS4Constants
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
import kotlin.random.Random

/**
 * Handles DualShock 4 6-axis IMU (3-axis Accelerometer + 3-axis Gyroscope).
 *
 * Uses:
 * 1. Android 12+ (API 31+) InputDevice.sensorManager for real controller motion hardware.
 * 2. Simulation engine when simulation mode is active.
 */
class MotionSensorManager(private val context: Context) {

    private val TAG = "MotionSensorManager"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _motionSensors = MutableStateFlow(MotionSensorsState())
    val motionSensors: StateFlow<MotionSensorsState> = _motionSensors.asStateFlow()

    private var activeDeviceSensorManager: SensorManager? = null
    private var simulationJob: Job? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            val values = event.values ?: return

            when (event.sensor?.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (values.size >= 3) {
                        _motionSensors.value = _motionSensors.value.copy(
                            accelX = values[0],
                            accelY = values[1],
                            accelZ = values[2],
                            isAvailable = true,
                            sensorStatus = "Active (Hardware IMU)"
                        )
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (values.size >= 3) {
                        _motionSensors.value = _motionSensors.value.copy(
                            gyroX = values[0],
                            gyroY = values[1],
                            gyroZ = values[2],
                            isAvailable = true,
                            sensorStatus = "Active (Hardware IMU)"
                        )
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun bindController(inputDeviceId: Int?) {
        unbindController()
        if (inputDeviceId == null) return

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
                        _motionSensors.value = _motionSensors.value.copy(
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
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding controller sensor manager", e)
        }
    }

    fun startSimulation() {
        stopSimulation()
        simulationJob = scope.launch {
            var step = 0f
            while (isActive) {
                step += 0.05f
                val noiseX = (Random.nextFloat() - 0.5f) * 0.1f
                val noiseY = (Random.nextFloat() - 0.5f) * 0.1f
                val noiseZ = (Random.nextFloat() - 0.5f) * 0.1f

                val accelX = (kotlin.math.sin(step) * 1.5f) + noiseX
                val accelY = 9.8f + (kotlin.math.cos(step * 0.7f) * 0.8f) + noiseY
                val accelZ = (kotlin.math.sin(step * 0.5f) * 0.6f) + noiseZ

                val gyroX = (kotlin.math.cos(step) * 0.12f)
                val gyroY = (kotlin.math.sin(step * 0.8f) * 0.15f)
                val gyroZ = (kotlin.math.cos(step * 1.2f) * 0.08f)

                _motionSensors.value = MotionSensorsState(
                    accelX = accelX,
                    accelY = accelY,
                    accelZ = accelZ,
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    isAvailable = true,
                    sensorStatus = "Simulated 6-Axis Telemetry"
                )
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
