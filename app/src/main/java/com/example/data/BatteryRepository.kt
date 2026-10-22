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
            isCharging = null,
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
            sourceDescription = "No battery data received"
        )
    )
    val batteryState: StateFlow<BatteryInfo> = _batteryState.asStateFlow()

    /**
     * Updates the battery state from a known authoritative [BatteryInfo] report.
     */
    fun updateBattery(info: BatteryInfo): BatteryInfo {
        _batteryState.value = info
        return info
    }

    /**
     * Checks if current telemetry is older than [maxAgeMs].
     * If so, marks the telemetry as stale and transitions [isCharging] to null
     * to prevent presenting an unverified charging state as current.
     */
    fun markStaleIfOutdated(maxAgeMs: Long = 15_000L): BatteryInfo {
        val current = _batteryState.value
        if (current.status == BatteryStatus.UNAVAILABLE && current.percentage == null) {
            return current
        }
        val elapsed = System.currentTimeMillis() - current.lastUpdated
        if (elapsed > maxAgeMs && !current.isStale) {
            Log.d(TAG, "Battery telemetry stale ($elapsed ms since last report). Marking status as unknown.")
            val staleInfo = current.copy(
                isCharging = null,
                isStale = true,
                sourceDescription = "Telemetry stale (last report ${elapsed / 1000}s ago)"
            )
            _batteryState.value = staleInfo
            return staleInfo
        }
        return current
    }

    /**
     * Extracts battery level from an Android [InputDevice].
     * Uses Android 12+ [InputDevice.getBatteryState] when available.
     * Maps BatteryState.STATUS_CHARGING (2) -> true,
     * STATUS_DISCHARGING (3), STATUS_NOT_CHARGING (4), STATUS_FULL (5) -> false,
     * STATUS_UNKNOWN (1) -> null.
     */
    fun extractFromInputDevice(device: InputDevice?): BatteryInfo {
        if (device == null) {
            return setUnavailable("No device connected")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val batteryState = device.batteryState
                val capacity = batteryState.capacity
                val status = batteryState.status
                // BatteryState constants:
                // STATUS_UNKNOWN = 1
                // STATUS_CHARGING = 2
                // STATUS_DISCHARGING = 3
                // STATUS_NOT_CHARGING = 4
                // STATUS_FULL = 5
                val isCharging: Boolean? = when (status) {
                    2 -> true
                    3, 4, 5 -> false
                    1 -> null
                    else -> null
                }

                if (!capacity.isNaN() && capacity >= 0f) {
                    val percentage = (capacity * 100).toInt().coerceIn(0, 100)
                    val batteryStatus = when {
                        isCharging == true -> BatteryStatus.CHARGING
                        percentage >= 60 -> BatteryStatus.HIGH
                        percentage >= 25 -> BatteryStatus.MEDIUM
                        percentage >= 1 -> BatteryStatus.LOW
                        else -> BatteryStatus.EMPTY
                    }
                    val result = BatteryInfo(
                        percentage = percentage,
                        status = batteryStatus,
                        isCharging = isCharging,
                        lastUpdated = System.currentTimeMillis(),
                        isStale = false,
                        sourceDescription = "Android InputDevice BatteryState (API 31+)"
                    )
                    _batteryState.value = result
                    return result
                } else if (isCharging != null) {
                    // Capacity is NaN but charging status is known
                    val result = BatteryInfo(
                        percentage = null,
                        status = if (isCharging) BatteryStatus.CHARGING else BatteryStatus.UNAVAILABLE,
                        isCharging = isCharging,
                        lastUpdated = System.currentTimeMillis(),
                        isStale = false,
                        sourceDescription = "Android InputDevice BatteryState (Status only)"
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
            isCharging = null,
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
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
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
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
            isCharging = null,
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
            sourceDescription = reason
        )
        _batteryState.value = info
        return info
    }

    /**
     * Sets simulated battery telemetry for testing and simulation mode.
     */
    fun setSimulatedBattery(percentage: Int, isCharging: Boolean? = false): BatteryInfo {
        val clamped = percentage.coerceIn(0, 100)
        val status = when {
            isCharging == true -> BatteryStatus.CHARGING
            clamped >= 60 -> BatteryStatus.HIGH
            clamped >= 25 -> BatteryStatus.MEDIUM
            clamped >= 1 -> BatteryStatus.LOW
            else -> BatteryStatus.EMPTY
        }
        val info = BatteryInfo(
            percentage = clamped,
            status = status,
            isCharging = isCharging,
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
            sourceDescription = "Simulation Telemetry"
        )
        _batteryState.value = info
        return info
    }
}
