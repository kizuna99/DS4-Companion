package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControllerManager
import com.example.model.ConnectionStatus
import com.example.model.TransportType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControllerManagerTest {

    private lateinit var context: Context
    private lateinit var controllerManager: ControllerManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        controllerManager = ControllerManager(context)
    }

    @After
    fun tearDown() {
        controllerManager.stopMonitoring()
    }

    @Test
    fun testInitialConnectionState() {
        // Without physical device attached, initial state should be DISCONNECTED
        val status = controllerManager.connectionState.value
        assertEquals(ConnectionStatus.DISCONNECTED, status)
        assertNull(controllerManager.activeController.value)
    }

    @Test
    fun testScanInputDeviceIds() {
        // Verify scanInputDeviceIds method runs and queries getDeviceIds without crashing
        val devices = controllerManager.scanInputDeviceIds()
        assertNotNull(devices)
    }

    @Test
    fun testSimulationModeTransitions() {
        // Enable simulation mode
        controllerManager.setSimulationMode(true)
        assertTrue(controllerManager.isSimulationMode.value)
        assertEquals(ConnectionStatus.CONNECTED, controllerManager.connectionState.value)

        val active = controllerManager.activeController.value
        assertNotNull(active)
        assertEquals(TransportType.SIMULATION, active?.transport)
        assertEquals("DualShock 4 [Safety Simulation]", active?.name)

        // Disconnect
        controllerManager.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, controllerManager.connectionState.value)
        assertNull(controllerManager.activeController.value)
    }

    @Test
    fun testStartAndStopMonitoringLifecycle() {
        // Verify monitoring starts and stops without throwing exceptions
        controllerManager.startMonitoring()
        controllerManager.stopMonitoring()
    }

    @Test
    fun testInputDeviceListenerCallbacks() {
        // Verify callbacks handle unknown/arbitrary device IDs gracefully
        controllerManager.onInputDeviceAdded(999)
        controllerManager.onInputDeviceRemoved(999)
        controllerManager.onInputDeviceChanged(999)
    }
}
