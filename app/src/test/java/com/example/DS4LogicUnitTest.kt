package com.example

import com.example.model.BatteryStatus
import com.example.model.DS4Constants
import com.example.model.LightBarColor
import com.example.protocol.DS4Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DS4LogicUnitTest {

    @Test
    fun testUsbOutputReportFormat() {
        val report = DS4Protocol.createUsbOutputReport(
            red = 255,
            green = 128,
            blue = 64,
            flashOn = 20,
            flashOff = 10,
            rumbleWeak = 50,
            rumbleStrong = 100
        )

        assertEquals(32, report.size)
        assertEquals(0x05.toByte(), report[0]) // Report ID
        assertEquals(0xFF.toByte(), report[1]) // Control flags
        assertEquals(50.toByte(), report[4])   // Rumble weak
        assertEquals(100.toByte(), report[5])  // Rumble strong
        assertEquals(255.toByte(), report[6])  // Red
        assertEquals(128.toByte(), report[7])  // Green
        assertEquals(64.toByte(), report[8])   // Blue
        assertEquals(20.toByte(), report[9])   // Flash On
        assertEquals(10.toByte(), report[10])  // Flash Off
    }

    @Test
    fun testBluetoothOutputReportFormatAndCrc() {
        val report = DS4Protocol.createBluetoothOutputReport(
            red = 0,
            green = 200,
            blue = 255
        )

        assertEquals(78, report.size)
        assertEquals(0x11.toByte(), report[0]) // Report ID
        assertEquals(0.toByte(), report[8])    // Red
        assertEquals(200.toByte(), report[9])  // Green
        assertEquals(255.toByte(), report[10]) // Blue

        // Verify CRC bytes are populated (not all zero)
        val hasNonZeroCrc = report[74] != 0.toByte() ||
                report[75] != 0.toByte() ||
                report[76] != 0.toByte() ||
                report[77] != 0.toByte()
        assertTrue("CRC32 bytes should be generated", hasNonZeroCrc)
    }

    @Test
    fun testBatteryParsingFromReportByte() {
        // High capacity: 9 (90%), discharging (0x09)
        val battHigh = DS4Protocol.parseBatteryByte(0x09.toByte())
        assertEquals(90, battHigh.percentage)
        assertEquals(BatteryStatus.HIGH, battHigh.status)
        assertFalse(battHigh.isCharging)

        // Medium capacity: 4 (40%), discharging (0x04)
        val battMed = DS4Protocol.parseBatteryByte(0x04.toByte())
        assertEquals(40, battMed.percentage)
        assertEquals(BatteryStatus.MEDIUM, battMed.status)
        assertFalse(battMed.isCharging)

        // Low capacity: 2 (20%), discharging (0x02)
        val battLow = DS4Protocol.parseBatteryByte(0x02.toByte())
        assertEquals(20, battLow.percentage)
        assertEquals(BatteryStatus.LOW, battLow.status)
        assertFalse(battLow.isCharging)

        // Charging flag bit 4 set (0x17 = 70% + charging)
        val battCharging = DS4Protocol.parseBatteryByte(0x17.toByte())
        assertEquals(70, battCharging.percentage)
        assertEquals(BatteryStatus.CHARGING, battCharging.status)
        assertTrue(battCharging.isCharging)

        // Empty capacity: 0 (0%)
        val battEmpty = DS4Protocol.parseBatteryByte(0x00.toByte())
        assertEquals(0, battEmpty.percentage)
        assertEquals(BatteryStatus.EMPTY, battEmpty.status)

        // Invalid raw byte (> 11 level) returns unavailable
        val battInvalid = DS4Protocol.parseBatteryByte(0x0F.toByte())
        assertNull(battInvalid.percentage)
        assertEquals(BatteryStatus.UNAVAILABLE, battInvalid.status)
    }

    @Test
    fun testAndroidBatteryStateParsing() {
        val batt = DS4Protocol.parseAndroidBatteryState(0.75f, false)
        assertEquals(75, batt.percentage)
        assertEquals(BatteryStatus.HIGH, batt.status)

        val battNan = DS4Protocol.parseAndroidBatteryState(Float.NaN, false)
        assertNull(battNan.percentage)
        assertEquals(BatteryStatus.UNAVAILABLE, battNan.status)
    }

    @Test
    fun testDs4HardwareDetection() {
        // Official Sony DS4 V1
        assertTrue(DS4Constants.isDualShock4(DS4Constants.SONY_VENDOR_ID, DS4Constants.SONY_DS4_V1_PID))
        // Official Sony DS4 V2
        assertTrue(DS4Constants.isDualShock4(DS4Constants.SONY_VENDOR_ID, DS4Constants.SONY_DS4_V2_PID))
        // Name-based Bluetooth detection
        assertTrue(DS4Constants.isDualShock4(0, 0, "Wireless Controller"))
        assertTrue(DS4Constants.isDualShock4(0, 0, "Sony DualShock 4 Controller"))
        // Unrelated device
        assertFalse(DS4Constants.isDualShock4(0x045E, 0x02EA, "Xbox Controller"))
    }

    @Test
    fun testLightBarColorBrightness() {
        val color = LightBarColor(red = 200, green = 100, blue = 50, brightness = 0.5f)
        assertEquals(100, color.effectiveRed)
        assertEquals(50, color.effectiveGreen)
        assertEquals(25, color.effectiveBlue)
        assertEquals("#643219", color.toHex())
    }
}
