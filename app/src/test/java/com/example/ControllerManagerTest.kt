package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControllerManager
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.ConnectionStatus
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.TransportType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testChargingStateUpdatesWithoutManualScan() {
        // Connect a test DS4 controller
        val initialChargingInfo = BatteryInfo(
            percentage = 70,
            status = BatteryStatus.CHARGING,
            isCharging = true,
            lastUpdated = System.currentTimeMillis(),
            isStale = false,
            sourceDescription = "Hardware report"
        )
        val testController = DS4DeviceInfo(
            id = "test_ds4",
            name = "Wireless Controller",
            transport = TransportType.BLUETOOTH,
            vendorId = DS4Constants.SONY_VENDOR_ID,
            productId = DS4Constants.SONY_DS4_V2_PID,
            batteryInfo = initialChargingInfo
        )
        controllerManager.connect(testController)

        assertEquals(ConnectionStatus.CONNECTED, controllerManager.connectionState.value)
        assertEquals(true, controllerManager.activeController.value?.batteryInfo?.isCharging)
        assertEquals(true, controllerManager.batteryRepository.batteryState.value.isCharging)

        // Unplug charger: hardware reports discharging (0x07 = 70% not charging)
        val freshDischarging = controllerManager.batteryRepository.parseDs4BatteryByte(0x07.toByte())
        assertEquals(false, freshDischarging.isCharging)
        assertEquals(BatteryStatus.HIGH, freshDischarging.status)

        // Verify active controller's battery state updates automatically
        controllerManager.refreshBatteryTelemetry()
        assertEquals(false, controllerManager.batteryRepository.batteryState.value.isCharging)
    }

    @Test
    fun testDisconnectResetsChargingState() {
        val testController = DS4DeviceInfo(
            id = "test_ds4_charging",
            name = "Wireless Controller",
            transport = TransportType.BLUETOOTH,
            vendorId = DS4Constants.SONY_VENDOR_ID,
            productId = DS4Constants.SONY_DS4_V2_PID,
            batteryInfo = BatteryInfo(
                percentage = 90,
                status = BatteryStatus.CHARGING,
                isCharging = true
            )
        )
        controllerManager.connect(testController)
        assertEquals(true, controllerManager.activeController.value?.batteryInfo?.isCharging)

        controllerManager.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, controllerManager.connectionState.value)
        assertNull(controllerManager.activeController.value)
        // Disconnected battery state must not display charging
        assertNull(controllerManager.batteryRepository.batteryState.value.isCharging)
        assertEquals(BatteryStatus.UNAVAILABLE, controllerManager.batteryRepository.batteryState.value.status)
    }

    @Test
    fun testStaleChargingStateClearsToUnknown() {
        val pastTimestamp = System.currentTimeMillis() - 25_000L
        val staleChargingInfo = BatteryInfo(
            percentage = 80,
            status = BatteryStatus.CHARGING,
            isCharging = true,
            lastUpdated = pastTimestamp,
            isStale = false,
            sourceDescription = "Old report"
        )
        val testController = DS4DeviceInfo(
            id = "test_stale",
            name = "Wireless Controller",
            transport = TransportType.BLUETOOTH,
            vendorId = DS4Constants.SONY_VENDOR_ID,
            productId = DS4Constants.SONY_DS4_V2_PID,
            batteryInfo = staleChargingInfo
        )
        controllerManager.connect(testController)
        controllerManager.batteryRepository.updateBattery(staleChargingInfo)

        // Stale evaluation marks isCharging = null and preserves 80%
        val staleResult = controllerManager.batteryRepository.markStaleIfOutdated(15_000L)
        assertTrue(staleResult.isStale)
        assertNull(staleResult.isCharging)
        assertEquals(80, staleResult.percentage)
    }
}
