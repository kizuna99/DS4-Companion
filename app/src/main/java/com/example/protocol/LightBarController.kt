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
 *    - Uses a process-wide persistent LightsSession that stays alive in memory across Activity
 *      destruction, configuration changes, and app backgrounding.
 *    - Updates colors via session.requestLights() on the existing session without closing/reopening,
 *      preventing session churn, LED flicker, or kernel reset to default color.
 *    - Operates WITHOUT requiring an active foreground service: Android system_server's LightsService
 *      maintains active device light states as long as the binder token is not explicitly closed and the
 *      process remains in memory (even in cached/background state).
 * 2. USB OTG Direct Hardware Protocol: Sends 32-byte Output Report 0x05 via controlTransfer & interrupt OUT.
 *    - Controller hardware microcode stores the RGB PWM duty cycle in hardware registers.
 *    - Hardware maintains the color indefinitely on USB power without background software polling.
 * 3. Simulation Mode: Updates reactive UI preview and logs telemetry immediately.
 * 4. Transparent error & compatibility messaging when hardware or OS constraints apply.
 */
class LightBarController(private val context: Context) {

    private val usbTransmitter = DS4UsbTransmitter(context)

    companion object {
        private const val TAG = "LightBarController"

        // Retain process-wide active lights session across screen navigation and app backgrounding.
        // Android's LightsService maintains the requested light state on the hardware as long as
        // the session's IBinder token remains alive in the process and is not explicitly closed.
        // This keeps the controller's light bar illuminated in the background without needing a foreground service.
        @Volatile
        private var activeLightsSession: AutoCloseable? = null

        @Volatile
        private var activeDeviceId: Int? = null

        @Volatile
        private var activeColor: LightBarColor = LightBarColor.PLAYSTATION_BLUE

        /**
         * Returns the currently applied LightBarColor across the process.
         */
        fun getActiveColor(): LightBarColor = activeColor

        /**
         * Checks whether an active LightsSession is held at the process level.
         */
        fun hasActiveSession(): Boolean = activeLightsSession != null

        /**
         * Explicitly closes the process-wide LightsSession.
         * Note: This must ONLY be invoked if the user explicitly turns off the light bar or
         * requests a complete hardware reset, NEVER during ordinary app backgrounding.
         */
        fun releaseSession() {
            try {
                activeLightsSession?.close()
                activeLightsSession = null
                activeDeviceId = null
                Log.d(TAG, "Process-wide LightsSession explicitly released.")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing LightsSession: ${e.message}")
            }
        }
    }

    fun applyColor(
        color: LightBarColor,
        activeDevice: DS4DeviceInfo?
    ): LightBarApplyResult {
        activeColor = color

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
                val lightsRequest = requestBuilder.build()
                val currentSession = activeLightsSession

                if (currentSession is LightsManager.LightsSession && activeDeviceId == device.id) {
                    // Fast-path: dynamically update existing open session without closing it.
                    // This avoids session teardown in Android's LightsService, eliminates flicker,
                    // and ensures the session stays active when the app is backgrounded.
                    try {
                        currentSession.requestLights(lightsRequest)
                    } catch (e: Exception) {
                        Log.d(TAG, "Existing LightsSession update failed (${e.message}), recreating session.")
                        try { currentSession.close() } catch (_: Exception) {}
                        val newSession = lightsManager.openSession()
                        newSession.requestLights(lightsRequest)
                        activeLightsSession = newSession
                        activeDeviceId = device.id
                    }
                } else {
                    // Open a new session and retain it at process scope
                    try {
                        currentSession?.close()
                    } catch (_: Exception) {}

                    val newSession = lightsManager.openSession()
                    newSession.requestLights(lightsRequest)
                    activeLightsSession = newSession
                    activeDeviceId = device.id
                }

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

    /**
     * Closes the active session if explicit cleanup is requested.
     * Note: NEVER call this during onPause or onStop.
     */
    fun close() {
        releaseSession()
    }
}
