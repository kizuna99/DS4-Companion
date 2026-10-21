package com.example.protocol

import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import java.util.zip.CRC32

object DS4Protocol {

    /**
     * DualShock 4 USB Output Report 0x05 (32 bytes)
     * Used over USB OTG to control Light Bar RGB and vibration motors.
     */
    fun createUsbOutputReport(
        red: Int,
        green: Int,
        blue: Int,
        flashOn: Int = 0,
        flashOff: Int = 0,
        rumbleWeak: Int = 0,
        rumbleStrong: Int = 0
    ): ByteArray {
        val report = ByteArray(32)
        report[0] = 0x05 // Report ID
        // Flag byte: 0x01 = rumble, 0x02 = light bar, 0x04 = flash rate
        report[1] = 0xFF.toByte()
        report[2] = 0x04
        report[3] = 0x00
        report[4] = rumbleWeak.coerceIn(0, 255).toByte()
        report[5] = rumbleStrong.coerceIn(0, 255).toByte()
        report[6] = red.coerceIn(0, 255).toByte()
        report[7] = green.coerceIn(0, 255).toByte()
        report[8] = blue.coerceIn(0, 255).toByte()
        report[9] = flashOn.coerceIn(0, 255).toByte()
        report[10] = flashOff.coerceIn(0, 255).toByte()
        return report
    }

    /**
     * DualShock 4 Bluetooth Output Report 0x11 (78 bytes)
     * Used over Bluetooth HID with CRC-32 checksum appended.
     */
    fun createBluetoothOutputReport(
        red: Int,
        green: Int,
        blue: Int,
        flashOn: Int = 0,
        flashOff: Int = 0,
        rumbleWeak: Int = 0,
        rumbleStrong: Int = 0
    ): ByteArray {
        val report = ByteArray(78)
        report[0] = 0x11 // Report ID
        report[1] = 0xC0.toByte() // Polling frequency and flags
        report[2] = 0x20
        report[3] = 0x07 // Enable rumble and light bar
        report[4] = 0x04
        report[5] = 0x00
        report[6] = rumbleWeak.coerceIn(0, 255).toByte()
        report[7] = rumbleStrong.coerceIn(0, 255).toByte()
        report[8] = red.coerceIn(0, 255).toByte()
        report[9] = green.coerceIn(0, 255).toByte()
        report[10] = blue.coerceIn(0, 255).toByte()
        report[11] = flashOn.coerceIn(0, 255).toByte()
        report[12] = flashOff.coerceIn(0, 255).toByte()

        // Calculate CRC-32 over BT HID header (0xA2) + report payload (bytes 0..73)
        val crcData = ByteArray(75)
        crcData[0] = 0xA2.toByte() // BT HID Output Report Transaction Type
        System.arraycopy(report, 0, crcData, 1, 74)

        val crc = CRC32()
        crc.update(crcData)
        val crcValue = crc.value.toInt()

        report[74] = (crcValue and 0xFF).toByte()
        report[75] = ((crcValue shr 8) and 0xFF).toByte()
        report[76] = ((crcValue shr 16) and 0xFF).toByte()
        report[77] = ((crcValue shr 24) and 0xFF).toByte()

        return report
    }

    /**
     * Parses DS4 hardware battery level byte.
     * Lower 4 bits: capacity 0..10 (0 = 0%, 10 = 100%)
     * Bit 4 (0x10): charging flag (1 = charging, 0 = discharging)
     */
    fun parseBatteryByte(rawByte: Byte): BatteryInfo {
        val unsigned = rawByte.toInt() and 0xFF
        val rawLevel = unsigned and 0x0F
        val isCharging = (unsigned and 0x10) != 0

        // If level is invalid or > 10, return unavailable
        if (rawLevel > 11) {
            return BatteryInfo(
                percentage = null,
                status = BatteryStatus.UNAVAILABLE,
                isCharging = isCharging,
                sourceDescription = "Invalid raw value (0x${Integer.toHexString(unsigned)})"
            )
        }

        // Capacity maps 0..10 to 0%..100%
        val percentage = (rawLevel * 10).coerceIn(0, 100)

        val status = when {
            isCharging -> BatteryStatus.CHARGING
            percentage >= 60 -> BatteryStatus.HIGH
            percentage >= 25 -> BatteryStatus.MEDIUM
            percentage >= 1 -> BatteryStatus.LOW
            else -> BatteryStatus.EMPTY
        }

        return BatteryInfo(
            percentage = percentage,
            status = status,
            isCharging = isCharging,
            sourceDescription = "Hardware report byte: 0x${Integer.toHexString(unsigned)}"
        )
    }

    /**
     * Maps Android 12+ BatteryState capacity (0.0 to 1.0 or Float.NaN)
     */
    fun parseAndroidBatteryState(capacity: Float, isCharging: Boolean): BatteryInfo {
        if (capacity.isNaN() || capacity < 0f) {
            return BatteryInfo(
                percentage = null,
                status = BatteryStatus.UNAVAILABLE,
                isCharging = isCharging,
                sourceDescription = "Android InputDevice reported capacity unavailable"
            )
        }

        val percentage = (capacity * 100).toInt().coerceIn(0, 100)
        val status = when {
            isCharging -> BatteryStatus.CHARGING
            percentage >= 60 -> BatteryStatus.HIGH
            percentage >= 25 -> BatteryStatus.MEDIUM
            percentage >= 1 -> BatteryStatus.LOW
            else -> BatteryStatus.EMPTY
        }

        return BatteryInfo(
            percentage = percentage,
            status = status,
            isCharging = isCharging,
            sourceDescription = "Android InputDevice BatteryState"
        )
    }
}
