package com.example.protocol

import android.content.Context
import android.graphics.Color as AndroidColor
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.util.Log
import android.view.InputDevice
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarApplyResult
import com.example.model.LightBarColor
import com.example.model.TransportType

/**
 * Controller responsible for DualShock 4 RGB Light Bar updates.
 *
 * Strategies:
 * 1. Android 12+ (API 31+) LightsManager: Controls the RGB light bar natively on Bluetooth or USB.
 * 2. USB OTG Direct Hardware Protocol: Sends 32-byte Output Report 0x05 via controlTransfer & interrupt OUT endpoint.
 * 3. Simulation Mode: Updates reactive UI preview and logs telemetry immediately.
 * 4. Transparent error & compatibility messaging when hardware or OS constraints apply.
 */
class LightBarController(private val context: Context) {

    private val TAG = "LightBarController"
    private val usbTransmitter = DS4UsbTransmitter(context)

    // Keep active session alive so the color remains illuminated
    private var activeLightsSession: AutoCloseable? = null

    fun applyColor(
        color: LightBarColor,
        activeDevice: DS4DeviceInfo?
    ): LightBarApplyResult {
        val argb = AndroidColor.argb(
            255,
            color.effectiveRed,
            color.effectiveGreen,
            color.effectiveBlue
        )

        // 1. Simulation Mode
        if (activeDevice?.transport == TransportType.SIMULATION) {
            return LightBarApplyResult(
                true,
                "Simulation: Light bar updated to ${color.toHex()} (${(color.brightness * 100).toInt()}%)."
            )
        }

        // 2. Try Android 12+ (API 31+) LightsManager directly on the active InputDevice
        val devId = activeDevice?.inputDeviceId
        if (devId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val inputDevice = InputDevice.getDevice(devId)
            if (inputDevice != null) {
                val lmResult = applyViaLightsManager(inputDevice, argb, color)
                if (lmResult != null && lmResult.success) {
                    return lmResult
                }
            }
        }

        // Check all gamepads / joysticks on system if activeDevice ID was not matched
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (id in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(id) ?: continue
                if (DS4Constants.isDualShock4(dev.vendorId, dev.productId, dev.name) ||
                    (dev.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
                ) {
                    val lmResult = applyViaLightsManager(dev, argb, color)
                    if (lmResult != null && lmResult.success) {
                        return lmResult
                    }
                }
            }
        }

        // 3. Try USB OTG Direct Hardware SET_REPORT / Interrupt OUT
        val usbDevice = usbTransmitter.findAttachedDs4UsbDevice()
        if (usbDevice != null) {
            return usbTransmitter.sendLightBarColor(color)
        }

        // 4. If no device is currently connected
        if (activeDevice == null) {
            return LightBarApplyResult(
                true,
                "Preview updated to ${color.toHex()} (${(color.brightness * 100).toInt()}%). Connect your DualShock 4 via Bluetooth or USB OTG to apply to physical hardware."
            )
        }

        // 5. Inform user clearly of the specific hardware limitation
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LightBarApplyResult(
                false,
                "Notice: DualShock 4 is connected via Bluetooth. Android ${Build.VERSION.RELEASE} (below Android 12) does not support Bluetooth controller light bar APIs without root. Connect via USB OTG adapter to change colors."
            )
        } else {
            LightBarApplyResult(
                false,
                "The connected controller's driver does not expose RGB light controls over the current transport. Connect via USB OTG adapter for direct hardware control."
            )
        }
    }

    private fun applyViaLightsManager(
        device: InputDevice,
        colorArgb: Int,
        color: LightBarColor
    ): LightBarApplyResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val lightsManager = device.lightsManager ?: return null
            val lights = lightsManager.lights
            if (lights.isEmpty()) return null

            val requestBuilder = LightsRequest.Builder()
            var addedLightsCount = 0

            for (light in lights) {
                if (light.hasRgbControl() || lights.size == 1) {
                    val lightState = LightState.Builder()
                        .setColor(colorArgb)
                        .build()
                    requestBuilder.addLight(light, lightState)
                    addedLightsCount++
                }
            }

            if (addedLightsCount == 0) {
                for (light in lights) {
                    val lightState = LightState.Builder().setColor(colorArgb).build()
                    requestBuilder.addLight(light, lightState)
                    addedLightsCount++
                }
            }

            if (addedLightsCount > 0) {
                try {
                    activeLightsSession?.close()
                } catch (_: Exception) {}

                val session = lightsManager.openSession()
                session.requestLights(requestBuilder.build())
                activeLightsSession = session

                LightBarApplyResult(
                    true,
                    "Light bar updated via Android LightsManager (${color.toHex()} at ${(color.brightness * 100).toInt()}%)."
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "LightsManager error: ${e.message}", e)
            null
        }
    }

    fun close() {
        try {
            activeLightsSession?.close()
            activeLightsSession = null
        } catch (_: Exception) {}
    }
}
