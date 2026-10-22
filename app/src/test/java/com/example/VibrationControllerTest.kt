package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DS4DeviceInfo
import com.example.model.TransportType
import com.example.protocol.MotorSelection
import com.example.protocol.VibrationController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VibrationControllerTest {

    private lateinit var context: Context
    private lateinit var vibrationController: VibrationController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        vibrationController = VibrationController(context)
    }

    @Test
    fun testVibration_SimulationMode() {
        val simDevice = DS4DeviceInfo(
            id = "sim_01",
            name = "Simulated DS4",
            transport = TransportType.SIMULATION
        )
        val result = vibrationController.testVibration(
            durationMs = 1500L,
            motor = MotorSelection.BOTH,
            activeDevice = simDevice
        )

        assertTrue(result.success)
        assertTrue(result.message.contains("Simulation"))
        assertTrue(result.message.contains("1500ms"))
    }

    @Test
    fun testVibration_LeftMotorOnly_Simulation() {
        val simDevice = DS4DeviceInfo(
            id = "sim_02",
            name = "Simulated DS4",
            transport = TransportType.SIMULATION
        )
        val result = vibrationController.testVibration(
            durationMs = 800L,
            motor = MotorSelection.LEFT_HEAVY,
            activeDevice = simDevice
        )

        assertTrue(result.success)
        assertTrue(result.message.contains("Left Motor Only"))
    }

    @Test
    fun testVibration_NoDeviceConnected() {
        val result = vibrationController.testVibration(
            durationMs = 1000L,
            motor = MotorSelection.BOTH,
            activeDevice = null
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("No controller connected"))
    }

    @Test
    fun testStopAllDoesNotThrow() {
        vibrationController.stopAll()
    }
}
