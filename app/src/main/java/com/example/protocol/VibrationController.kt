package com.example.protocol

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.util.Log
import android.view.InputDevice
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VibrationTestResult(
    val success: Boolean,
    val message: String
)

enum class MotorSelection(val label: String, val description: String) {
    BOTH("Both Motors", "Left (Heavy) + Right (Light) rumble"),
    LEFT_HEAVY("Left Motor Only", "Heavy low-frequency rumble"),
    RIGHT_LIGHT("Right Motor Only", "Light high-frequency buzz")
}

/**
 * Controller to test and toggle DualShock 4 dual-motor rumble functionality.
 *
 * Supports:
 * 1. Android 12+ (API 31+) InputDevice VibratorManager (channel-separated dual motor).
 * 2. Standard InputDevice Vibrator.
 * 3. USB OTG Direct Hardware Output Report 0x05.
 * 4. Simulation mode.
 * 5. On/Off toggle and duration tests.
 */
class VibrationController(private val context: Context) {

    private val TAG = "VibrationController"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val usbTransmitter = DS4UsbTransmitter(context)

    private val _isVibrating = MutableStateFlow(false)
    val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    private var activeVibrationJob: Job? = null

    fun testVibration(
        durationMs: Long = 1000L,
        motor: MotorSelection = MotorSelection.BOTH,
        activeDevice: DS4DeviceInfo?,
        currentColor: LightBarColor = LightBarController.getActiveColor()
    ): VibrationTestResult {
        val leftIntensity = when (motor) {
            MotorSelection.BOTH, MotorSelection.LEFT_HEAVY -> 255
            MotorSelection.RIGHT_LIGHT -> 0
        }
        val rightIntensity = when (motor) {
            MotorSelection.BOTH, MotorSelection.RIGHT_LIGHT -> 255
            MotorSelection.LEFT_HEAVY -> 0
        }

        // 1. Simulation Mode
        if (activeDevice?.transport == TransportType.SIMULATION) {
            triggerVibrationState(durationMs)
            return VibrationTestResult(
                true,
                "Simulation: ${motor.label} vibration active for ${durationMs}ms."
            )
        }

        // 2. Try Android InputDevice VibratorManager / Vibrator
        val devId = activeDevice?.inputDeviceId
        if (devId != null) {
            val inputDev = InputDevice.getDevice(devId)
            if (inputDev != null) {
                val inputResult = vibrateInputDevice(inputDev, durationMs, leftIntensity, rightIntensity, motor)
                if (inputResult != null && inputResult.success) {
                    triggerVibrationState(durationMs)
                    return inputResult
                }
            }
        }

        // Also check all joystick or gamepad InputDevices
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (DS4Constants.isDualShock4(dev.vendorId, dev.productId, dev.name) ||
                (dev.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
                (dev.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD)
            ) {
                val inputResult = vibrateInputDevice(dev, durationMs, leftIntensity, rightIntensity, motor)
                if (inputResult != null && inputResult.success) {
                    triggerVibrationState(durationMs)
                    return inputResult
                }
            }
        }

        // 3. Try USB OTG Direct Hardware Output Report 0x05
        val usbDev = usbTransmitter.findAttachedDs4UsbDevice()
        if (usbDev != null) {
            triggerVibrationState(durationMs)
            return vibrateUsbOtg(durationMs, leftIntensity, rightIntensity, motor, currentColor)
        }

        if (activeDevice == null) {
            return VibrationTestResult(
                false,
                "No controller connected. Connect your DualShock 4 via Bluetooth or USB OTG to test vibration motors."
            )
        }

        val transportDesc = when (activeDevice.transport) {
            TransportType.BLUETOOTH -> "Bluetooth (HID)"
            TransportType.USB_OTG -> "USB OTG"
            TransportType.SYSTEM_INPUT_DEVICE -> "System Input Device"
            TransportType.SIMULATION -> "Simulation"
        }
        return VibrationTestResult(
            false,
            "Connected controller did not accept vibration over $transportDesc. Connect via USB OTG for direct hardware control."
        )
    }

    private fun triggerVibrationState(durationMs: Long) {
        activeVibrationJob?.cancel()
        activeVibrationJob = scope.launch {
            _isVibrating.value = true
            delay(durationMs)
            _isVibrating.value = false
        }
    }

    fun stopVibration(
        activeDevice: DS4DeviceInfo?,
        currentColor: LightBarColor = LightBarController.getActiveColor()
    ): VibrationTestResult {
        activeVibrationJob?.cancel()
        activeVibrationJob = null
        _isVibrating.value = false

        // Stop on InputDevice
        val devId = activeDevice?.inputDeviceId
        if (devId != null) {
            val inputDev = InputDevice.getDevice(devId)
            if (inputDev != null) {
                stopInputDeviceVibration(inputDev)
            }
        }
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            stopInputDeviceVibration(dev)
        }

        // Stop on USB OTG, preserving current light bar color
        val usbDev = usbTransmitter.findAttachedDs4UsbDevice()
        if (usbDev != null) {
            val stopReport = DS4Protocol.createUsbOutputReport(
                red = currentColor.effectiveRed,
                green = currentColor.effectiveGreen,
                blue = currentColor.effectiveBlue,
                flashOn = currentColor.flashOn,
                flashOff = currentColor.flashOff,
                rumbleWeak = 0,
                rumbleStrong = 0
            )
            sendUsbRawReport(stopReport)
        }

        return VibrationTestResult(true, "Vibration stopped.")
    }

    private fun stopInputDeviceVibration(device: InputDevice) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                device.vibratorManager?.let { vm ->
                    for (id in vm.vibratorIds) {
                        vm.getVibrator(id).cancel()
                    }
                    vm.defaultVibrator.cancel()
                }
            }
            @Suppress("DEPRECATION")
            device.vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping vibration", e)
        }
    }

    private fun vibrateInputDevice(
        device: InputDevice,
        durationMs: Long,
        leftIntensity: Int,
        rightIntensity: Int,
        motor: MotorSelection
    ): VibrationTestResult? {
        try {
            val isBluetooth = (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                    (device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
            val transportLabel = if (isBluetooth) "Bluetooth HID" else "InputDevice"

            // 1. Android 12+ (API 31+) VibratorManager with multi-channel and CombinedVibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = device.vibratorManager
                if (vm != null) {
                    val ids = vm.vibratorIds
                    if (ids.size >= 2) {
                        try {
                            val comboBuilder = android.os.CombinedVibration.startParallel()
                            if (leftIntensity > 0) {
                                comboBuilder.addVibrator(
                                    ids[0],
                                    VibrationEffect.createOneShot(durationMs, leftIntensity)
                                )
                            }
                            if (rightIntensity > 0) {
                                comboBuilder.addVibrator(
                                    ids[1],
                                    VibrationEffect.createOneShot(durationMs, rightIntensity)
                                )
                            }
                            vm.vibrate(comboBuilder.combine())
                            return VibrationTestResult(
                                true,
                                "$transportLabel: ${motor.label} active via VibratorManager (${durationMs}ms)."
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "CombinedVibration failed, trying individual vibrators: ${e.message}")
                            if (leftIntensity > 0) {
                                vm.getVibrator(ids[0]).vibrate(
                                    VibrationEffect.createOneShot(durationMs, leftIntensity)
                                )
                            }
                            if (rightIntensity > 0) {
                                vm.getVibrator(ids[1]).vibrate(
                                    VibrationEffect.createOneShot(durationMs, rightIntensity)
                                )
                            }
                            return VibrationTestResult(
                                true,
                                "$transportLabel: ${motor.label} active via dual Vibrators (${durationMs}ms)."
                            )
                        }
                    } else if (ids.size == 1) {
                        val maxIntensity = maxOf(leftIntensity, rightIntensity).coerceAtLeast(1)
                        vm.getVibrator(ids[0]).vibrate(
                            VibrationEffect.createOneShot(durationMs, maxIntensity)
                        )
                        return VibrationTestResult(
                            true,
                            "$transportLabel: Single-channel vibration active via VibratorManager (${durationMs}ms)."
                        )
                    } else {
                        // Some Bluetooth HID drivers have empty vibratorIds, but defaultVibrator works
                        try {
                            val maxIntensity = maxOf(leftIntensity, rightIntensity).coerceAtLeast(1)
                            vm.defaultVibrator.vibrate(
                                VibrationEffect.createOneShot(durationMs, maxIntensity)
                            )
                            return VibrationTestResult(
                                true,
                                "$transportLabel: Rumble active via default VibratorManager (${durationMs}ms)."
                            )
                        } catch (e: Exception) {
                            Log.d(TAG, "VibratorManager default vibrate fallback: ${e.message}")
                        }
                    }
                }
            }

            // 2. Fallback to InputDevice Vibrator (API 26+)
            // CRITICAL: Do NOT check vibrator.hasVibrator() == true, because Android's
            // Bluetooth HID driver often returns false for hasVibrator() on DS4 controllers
            // even though calling vibrate() successfully dispatches rumble.
            @Suppress("DEPRECATION")
            val vibrator = device.vibrator
            if (vibrator != null) {
                try {
                    val maxIntensity = maxOf(leftIntensity, rightIntensity).coerceAtLeast(1)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, maxIntensity))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(durationMs)
                    }
                    return VibrationTestResult(
                        true,
                        "$transportLabel: Rumble dispatched via InputDevice Vibrator (${durationMs}ms)."
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "InputDevice vibrator call failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate InputDevice", e)
        }
        return null
    }

    private fun vibrateUsbOtg(
        durationMs: Long,
        leftIntensity: Int,
        rightIntensity: Int,
        motor: MotorSelection,
        color: LightBarColor
    ): VibrationTestResult {
        return try {
            val startReport = DS4Protocol.createUsbOutputReport(
                red = color.effectiveRed,
                green = color.effectiveGreen,
                blue = color.effectiveBlue,
                flashOn = color.flashOn,
                flashOff = color.flashOff,
                rumbleWeak = rightIntensity,
                rumbleStrong = leftIntensity
            )
            val stopReport = DS4Protocol.createUsbOutputReport(
                red = color.effectiveRed,
                green = color.effectiveGreen,
                blue = color.effectiveBlue,
                flashOn = color.flashOn,
                flashOff = color.flashOff,
                rumbleWeak = 0,
                rumbleStrong = 0
            )

            // Send start vibration
            activeVibrationJob?.cancel()
            activeVibrationJob = scope.launch {
                _isVibrating.value = true
                sendUsbRawReport(startReport)
                delay(durationMs)
                sendUsbRawReport(stopReport)
                _isVibrating.value = false
            }

            VibrationTestResult(
                true,
                "Vibration sent via USB OTG Report 0x05 (${motor.label}, ${durationMs}ms)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "USB vibration failed", e)
            VibrationTestResult(false, "USB vibration error: ${e.message}")
        }
    }

    private fun sendUsbRawReport(report: ByteArray) {
        try {
            val usbDevice = usbTransmitter.findAttachedDs4UsbDevice() ?: return
            val manager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager ?: return
            val conn = manager.openDevice(usbDevice) ?: return
            try {
                val iface = if (usbDevice.interfaceCount > 0) usbDevice.getInterface(0) else null
                if (iface != null) {
                    conn.claimInterface(iface, true)
                }
                val reqType = 0x21
                val transferred = conn.controlTransfer(reqType, 0x09, (0x02 shl 8) or 0x05, iface?.id ?: 0, report, report.size, 1000)
                if (transferred < 0 && iface != null) {
                    for (i in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(i)
                        if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                            conn.bulkTransfer(ep, report, report.size, 1000)
                            break
                        }
                    }
                }
                if (iface != null) {
                    conn.releaseInterface(iface)
                }
            } finally {
                conn.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendUsbRawReport error", e)
        }
    }

    fun stopAll() {
        activeVibrationJob?.cancel()
        activeVibrationJob = null
        _isVibrating.value = false
    }
}
