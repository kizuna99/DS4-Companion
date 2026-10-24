package com.example

import com.example.data.BatteryRepository
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
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
class BatteryRepositoryTest {

    private lateinit var batteryRepository: BatteryRepository

    @Before
    fun setup() {
        batteryRepository = BatteryRepository()
    }

    @Test
    fun testInitialBatteryStateIsUnavailable() {
        val state = batteryRepository.batteryState.value
        assertEquals(BatteryStatus.UNAVAILABLE, state.status)
        assertNull(state.percentage)
        assertNull(state.isCharging)
        assertFalse(state.isStale)
    }

    @Test
    fun testParseDs4BatteryByte_HighCapacity() {
        // 0x0A = 10 (100%), discharging
        val info = batteryRepository.parseDs4BatteryByte(0x0A.toByte())
        assertEquals(100, info.percentage)
        assertEquals(BatteryStatus.HIGH, info.status)
        assertEquals(false, info.isCharging)
    }

    @Test
    fun testParseDs4BatteryByte_Charging() {
        // 0x17 = 7 (70%), charging flag set (0x10)
        val info = batteryRepository.parseDs4BatteryByte(0x17.toByte())
        assertEquals(70, info.percentage)
        assertEquals(BatteryStatus.CHARGING, info.status)
        assertEquals(true, info.isCharging)
    }

    @Test
    fun testParseDs4BatteryByte_MediumCapacity() {
        // 0x04 = 4 (40%), discharging
        val info = batteryRepository.parseDs4BatteryByte(0x04.toByte())
        assertEquals(40, info.percentage)
        assertEquals(BatteryStatus.MEDIUM, info.status)
        assertEquals(false, info.isCharging)
    }

    @Test
    fun testParseDs4BatteryByte_LowCapacity() {
        // 0x02 = 2 (20%), discharging
        val info = batteryRepository.parseDs4BatteryByte(0x02.toByte())
        assertEquals(20, info.percentage)
        assertEquals(BatteryStatus.LOW, info.status)
        assertEquals(false, info.isCharging)
    }

    @Test
    fun testParseDs4BatteryByte_EmptyCapacity() {
        // 0x00 = 0 (0%), discharging
        val info = batteryRepository.parseDs4BatteryByte(0x00.toByte())
        assertEquals(0, info.percentage)
        assertEquals(BatteryStatus.EMPTY, info.status)
        assertEquals(false, info.isCharging)
    }

    @Test
    fun testParseDs4BatteryByte_InvalidValueReturnsUnavailable() {
        // 0x0F = rawLevel 15 (> 11 invalid)
        val info = batteryRepository.parseDs4BatteryByte(0x0F.toByte())
        assertNull(info.percentage)
        assertEquals(BatteryStatus.UNAVAILABLE, info.status)
        assertNull(info.isCharging)
    }

    @Test
    fun testParseUsbHidReport() {
        val usbReport = ByteArray(64)
        usbReport[0] = 0x01 // USB report ID
        usbReport[30] = 0x08 // 80%, discharging

        val info = batteryRepository.parseDs4HidReport(usbReport)
        assertEquals(80, info.percentage)
        assertEquals(BatteryStatus.HIGH, info.status)
        assertEquals(false, info.isCharging)
    }

    @Test
    fun testParseBluetoothHidReport() {
        val btReport = ByteArray(78)
        btReport[0] = 0x11 // BT report ID
        btReport[32] = 0x16 // 60%, charging

        val info = batteryRepository.parseDs4HidReport(btReport)
        assertEquals(60, info.percentage)
        assertEquals(BatteryStatus.CHARGING, info.status)
        assertEquals(true, info.isCharging)
    }

    @Test
    fun testParseShortReportReturnsUnavailable() {
        val shortReport = ByteArray(10)
        val info = batteryRepository.parseDs4HidReport(shortReport)
        assertNull(info.percentage)
        assertEquals(BatteryStatus.UNAVAILABLE, info.status)
        assertNull(info.isCharging)
    }

    @Test
    fun testSetUnavailable() {
        val info = batteryRepository.setUnavailable("Custom disconnect reason")
        assertNull(info.percentage)
        assertEquals(BatteryStatus.UNAVAILABLE, info.status)
        assertNull(info.isCharging)
        assertEquals("Custom disconnect reason", info.sourceDescription)
    }

    @Test
    fun testSetSimulatedBattery() {
        val info = batteryRepository.setSimulatedBattery(85, false)
        assertEquals(85, info.percentage)
        assertEquals(BatteryStatus.HIGH, info.status)
        assertEquals(false, info.isCharging)
        assertEquals(info, batteryRepository.batteryState.value)
    }

    @Test
    fun testChargingToDischargingTransition() {
        // Step 1: Controller is plugged in and charging (0x18 = 80% charging)
        val chargingReport = batteryRepository.parseDs4BatteryByte(0x18.toByte())
        assertEquals(80, chargingReport.percentage)
        assertEquals(true, chargingReport.isCharging)
        assertEquals(BatteryStatus.CHARGING, chargingReport.status)

        // Step 2: Cable is removed. Controller reports discharging (0x08 = 80% discharging)
        val dischargingReport = batteryRepository.parseDs4BatteryByte(0x08.toByte())
        assertEquals(80, dischargingReport.percentage)
        assertEquals(false, dischargingReport.isCharging)
        assertEquals(BatteryStatus.HIGH, dischargingReport.status)

        // StateFlow reflects the latest updated state without manual scanning
        val current = batteryRepository.batteryState.value
        assertEquals(false, current.isCharging)
        assertEquals(80, current.percentage)
    }

    @Test
    fun testStaleTelemetryHandling() {
        // Set initial report from 20 seconds ago
        val pastTimestamp = System.currentTimeMillis() - 20_000L
        val oldChargingInfo = BatteryInfo(
            percentage = 90,
            status = BatteryStatus.CHARGING,
            isCharging = true,
            lastUpdated = pastTimestamp,
            isStale = false,
            sourceDescription = "Past reading"
        )
        batteryRepository.updateBattery(oldChargingInfo)

        // Telemetry is older than 15s -> markStaleIfOutdated must transition isCharging to null
        val stale = batteryRepository.markStaleIfOutdated(15_000L)
        assertTrue(stale.isStale)
        assertNull(stale.isCharging)
        // Percentage remains preserved as last known reading
        assertEquals(90, stale.percentage)
    }
}
