package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.model.TransportType
import com.example.protocol.LightBarController
import com.example.protocol.MotorSelection
import com.example.protocol.VibrationController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LightBarControllerTest {

    private lateinit var context: Context
    private lateinit var lightBarController: LightBarController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        lightBarController = LightBarController(context)
    }

    @Test
    fun testApplyColor_SimulationMode() {
        val simDevice = DS4DeviceInfo(
            id = "sim_01",
            name = "Simulated DS4",
            transport = TransportType.SIMULATION
        )
        val color = LightBarColor.COMBAT_RED
        val result = lightBarController.applyColor(color, simDevice)

        assertTrue(result.success)
        assertTrue(result.message.contains("Simulation"))
        assertTrue(result.message.contains(color.toHex()))
        assertEquals(color, LightBarController.getActiveColor())
    }

    @Test
    fun testApplyColor_NullDevice_UpdatesPreview() {
        val color = LightBarColor.ELECTRIC_CYAN
        val result = lightBarController.applyColor(color, null)

        assertTrue(result.success)
        assertTrue(result.message.contains("Preview updated"))
        assertTrue(result.message.contains(color.toHex()))
        assertEquals(color, LightBarController.getActiveColor())
    }

    @Test
    fun testProcessWideActiveColorRetentionAcrossInstances() {
        val customColor = LightBarColor.NEON_PURPLE
        lightBarController.applyColor(customColor, null)

        assertEquals(customColor, LightBarController.getActiveColor())

        // Create a new controller instance as would happen across screen transitions or backgrounding
        val newController = LightBarController(context)
        assertEquals(customColor, LightBarController.getActiveColor())

        val appliedColor = LightBarColor.ACID_GREEN
        newController.applyColor(appliedColor, null)
        assertEquals(appliedColor, LightBarController.getActiveColor())
    }

    @Test
    fun testExplicitReleaseSession() {
        LightBarController.releaseSession()
        // Ensure releasing session does not crash and leaves active color intact
        assertNotNull(LightBarController.getActiveColor())
    }

    @Test
    fun testVibrationPreservesActiveColor() {
        val vibrationController = VibrationController(context)
        val purple = LightBarColor.NEON_PURPLE
        lightBarController.applyColor(purple, null)

        val simDevice = DS4DeviceInfo(
            id = "sim_vib",
            name = "Simulated DS4",
            transport = TransportType.SIMULATION
        )

        // Trigger vibration
        val testResult = vibrationController.testVibration(
            durationMs = 500L,
            motor = MotorSelection.BOTH,
            activeDevice = simDevice,
            currentColor = purple
        )
        assertTrue(testResult.success)

        // Stop vibration
        val stopResult = vibrationController.stopVibration(simDevice, purple)
        assertTrue(stopResult.success)

        // Active color remains intact
        assertEquals(purple, LightBarController.getActiveColor())
    }
}
