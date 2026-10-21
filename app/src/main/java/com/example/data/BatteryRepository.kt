package com.example.data

import android.os.Build
import android.util.Log
import android.view.InputDevice
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository responsible for extracting, parsing, and managing battery data for
 * DualShock 4 controllers from:
 * 1. Android [InputDevice] BatteryState API (Android 12+, API 31+).
 * 2. Raw hardware HID Input Reports over USB OTG and Bluetooth.
 * 3. Graceful fallback to [BatteryStatus.UNAVAILABLE] when hardware or OS does not report metrics.
 */
class BatteryRepository {

    private val TAG = "BatteryRepository"

    private val _batteryState = MutableStateFlow(
        BatteryInfo(
            percentage = null,
            status = BatteryStatus.UNAVAILABLE,
            isCharging = false,
            sourceDescription = "No battery data received"
        )
    )
    val batteryState: StateFlow<BatteryInfo> = _batteryState.asStateFlow()

    /**
     * Extracts battery level from an Android [InputDevice].
     * Uses Android 12+ [InputDevice.getBatteryState] when available.
     * If capacity is not reported or device is below Android 12, returns an honest unavailable status.
     */
    fun extractFromInputDevice(device: InputDevice?): BatteryInfo {
        if (device == null) {
            val result = BatteryInfo(
                percentage = null,
                status = BatteryStatus.UNAVAILABLE,
                isCharging = false,
                sourceDescription = "No device connected"
            )
            _batteryState.value = result
            return result
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val batteryState = device.batteryState
                val capacity = batteryState.capacity
                val status = batteryState.status
                // BatteryState.STATUS_CHARGING == 2, BatteryState.STATUS_FULL == 5
                val isCharging = status == 2

                if (!capacity.isNaN() && capacity >= 0f) {
                    val percentage = (capacity * 100).toInt().coerceIn(0, 100)
                    val batteryStatus = when {
                        isCharging -> BatteryStatus.CHARGING
                        percentage >= 60 -> BatteryStatus.HIGH
                        percentage >= 25 -> BatteryStatus.MEDIUM
                        percentage >= 1 -> BatteryStatus.LOW
                        else -> BatteryStatus.EMPTY
                    }
                    val result = BatteryInfo(
                        percentage = percentage,
                        status = batteryStatus,
                        isCharging = isCharging,
                        sourceDescription = "Android InputDevice BatteryState (API 31+)"
                    )
                    _batteryState.value = result
                    return result
                }
            } catch (e: Throwable) {
                Log.d(TAG, "InputDevice BatteryState query error: ${e.message}")
            }
        }

        val result = BatteryInfo(
            percentage = null,
            status = BatteryStatus.UNAVAILABLE,
            isCharging = false,
            sourceDescription = "Battery level not reported by OS kernel driver"
        )
        _batteryState.value = result
        return result
    }

    /**
     * Parses DualShock 4 HID Input Reports:
     * - USB Input Report 0x01 (64 bytes): byte 30 contains battery capacity & charging flag.
     * - Bluetooth Input Report 0x11 (78 bytes): byte 32 contains battery capacity & charging flag.
     */
    fun parseDs4HidReport(reportBytes: ByteArray): BatteryInfo {
        if (reportBytes.isEmpty()) {
            return setUnavailable("Empty HID report buffer")
        }

        val reportId = reportBytes[0].toInt() and 0xFF

        val batteryByte = when {
            // USB Input Report 0x01
            reportId == 0x01 && reportBytes.size >= 31 -> {
                reportBytes[30]
            }
            // Bluetooth Extended Input Report 0x11
            reportId == 0x11 && reportBytes.size >= 33 -> {
                reportBytes[32]
            }
            // Standard report fallback if buffer length is at least 31 bytes
            reportBytes.size >= 31 -> {
                reportBytes[30]
            }
            else -> {
                return setUnavailable("Report length (${reportBytes.size} bytes) too short for DS4 battery frame")
            }
        }

        val result = parseDs4BatteryByte(batteryByte)
        _batteryState.value = result
        return result
    }

    /**
     * Parses the DualShock 4 battery byte:
     * - Lower 4 bits (0x0F): capacity (0..10, where 0=0%, 10=100%).
     * - Bit 4 (0x10): charging flag (1=charging, 0=discharging).
     */
    fun parseDs4BatteryByte(rawByte: Byte): BatteryInfo {
        val unsigned = rawByte.toInt() and 0xFF
        val rawLevel = unsigned and 0x0F
        val isCharging = (unsigned and 0x10) != 0

        // If level exceeds 11, the byte is invalid or not a valid battery frame
        if (rawLevel > 11) {
            return setUnavailable("Invalid battery frame byte: 0x${Integer.toHexString(unsigned).uppercase()}")
        }

        val percentage = (rawLevel * 10).coerceIn(0, 100)
        val status = when {
            isCharging -> BatteryStatus.CHARGING
            percentage >= 60 -> BatteryStatus.HIGH
            percentage >= 25 -> BatteryStatus.MEDIUM
            percentage >= 1 -> BatteryStatus.LOW
            else -> BatteryStatus.EMPTY
        }

        val result = BatteryInfo(
            percentage = percentage,
            status = status,
            isCharging = isCharging,
            sourceDescription = "DS4 HID report (0x${Integer.toHexString(unsigned).uppercase()})"
        )
        _batteryState.value = result
        return result
    }

    /**
     * Sets battery state to unavailable with an explanatory description.
     */
    fun setUnavailable(reason: String = "Battery unavailable"): BatteryInfo {
        val info = BatteryInfo(
            percentage = null,
            status = BatteryStatus.UNAVAILABLE,
            isCharging = false,
            sourceDescription = reason
        )
        _batteryState.value = info
        return info
    }

    /**
     * Sets simulated battery telemetry for testing and simulation mode.
     */
    fun setSimulatedBattery(percentage: Int, isCharging: Boolean = false): BatteryInfo {
        val clamped = percentage.coerceIn(0, 100)
        val status = when {
            isCharging -> BatteryStatus.CHARGING
            clamped >= 60 -> BatteryStatus.HIGH
            clamped >= 25 -> BatteryStatus.MEDIUM
            clamped >= 1 -> BatteryStatus.LOW
            else -> BatteryStatus.EMPTY
        }
        val info = BatteryInfo(
            percentage = clamped,
            status = status,
            isCharging = isCharging,
            sourceDescription = "Simulation Telemetry"
        )
        _batteryState.value = info
        return info
    }
}
