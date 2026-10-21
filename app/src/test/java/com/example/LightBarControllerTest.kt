package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.model.TransportType
import com.example.protocol.LightBarController
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
    }

    @Test
    fun testApplyColor_NullDevice_UpdatesPreview() {
        val color = LightBarColor.ELECTRIC_CYAN
        val result = lightBarController.applyColor(color, null)

        assertTrue(result.success)
        assertTrue(result.message.contains("Preview updated"))
        assertTrue(result.message.contains(color.toHex()))
    }
}
