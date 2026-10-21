package com.example.protocol

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.DS4Constants
import com.example.model.LightBarApplyResult
import com.example.model.LightBarColor

class DS4UsbTransmitter(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val TAG = "DS4UsbTransmitter"

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.ds4companion.USB_PERMISSION"
    }

    fun findAttachedDs4UsbDevice(): UsbDevice? {
        val manager = usbManager ?: return null
        for ((_, device) in manager.deviceList) {
            if (DS4Constants.isDualShock4(device.vendorId, device.productId, device.deviceName ?: "")) {
                return device
            }
        }
        return null
    }

    fun hasUsbPermission(device: UsbDevice): Boolean {
        return usbManager?.hasPermission(device) == true
    }

    fun requestUsbPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager?.requestPermission(device, permissionIntent)
    }

    /**
     * Sends DS4 Light Bar color via USB HID Output Report 0x05 using controlTransfer.
     */
    fun sendLightBarColor(color: LightBarColor): LightBarApplyResult {
        val manager = usbManager
            ?: return LightBarApplyResult(false, "USB service is unavailable on this device.")

        val device = findAttachedDs4UsbDevice()
            ?: return LightBarApplyResult(
                false,
                "No DualShock 4 controller detected via USB OTG. Connect via USB OTG for direct hardware light bar control."
            )

        if (!manager.hasPermission(device)) {
            requestUsbPermission(device)
            return LightBarApplyResult(
                false,
                "USB permission required. Tap Allow on the system prompt to grant access to the controller."
            )
        }

        var connection: UsbDeviceConnection? = null
        var usbInterface: UsbInterface? = null

        return try {
            connection = manager.openDevice(device)
            if (connection == null) {
                return LightBarApplyResult(false, "Failed to open USB connection to DualShock 4.")
            }

            // DualShock 4 HID is on Interface 0 (or first interface)
            if (device.interfaceCount > 0) {
                usbInterface = device.getInterface(0)
                connection.claimInterface(usbInterface, true)
            }

            val report = DS4Protocol.createUsbOutputReport(
                red = color.effectiveRed,
                green = color.effectiveGreen,
                blue = color.effectiveBlue,
                flashOn = color.flashOn,
                flashOff = color.flashOff
            )

            // HID Class SET_REPORT: requestType 0x21 (USB_DIR_OUT | USB_TYPE_CLASS | RECIPIENT_INTERFACE)
            val requestType = 0x21
            val request = 0x09 // SET_REPORT
            val value = (0x02 shl 8) or 0x05 // Output report 0x05
            val index = usbInterface?.id ?: 0

            var transferred = connection.controlTransfer(
                requestType,
                request,
                value,
                index,
                report,
                report.size,
                1000
            )

            // If control transfer failed, try sending via Interrupt/Bulk OUT endpoint
            if (transferred < 0 && usbInterface != null) {
                for (i in 0 until usbInterface.endpointCount) {
                    val ep = usbInterface.getEndpoint(i)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        val endpointResult = connection.bulkTransfer(ep, report, report.size, 1000)
                        if (endpointResult >= 0) {
                            transferred = endpointResult
                            break
                        }
                    }
                }
            }

            if (transferred >= 0) {
                LightBarApplyResult(
                    true,
                    "Command delivered via USB HID Report 0x05 (${color.toHex()} at ${(color.brightness * 100).toInt()}%)."
                )
            } else {
                LightBarApplyResult(
                    false,
                    "USB transfer returned code $transferred. Please reconnect the USB OTG adapter and try again."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending USB light bar report", e)
            LightBarApplyResult(false, "USB error: ${e.localizedMessage}")
        } finally {
            try {
                if (usbInterface != null && connection != null) {
                    connection.releaseInterface(usbInterface)
                }
                connection?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing USB connection", e)
            }
        }
    }

    /**
     * Reads battery state from USB OTG connected DS4 if accessible.
     */
    fun readBatteryOverUsb(): BatteryInfo? {
        val manager = usbManager ?: return null
        val device = findAttachedDs4UsbDevice() ?: return null
        if (!manager.hasPermission(device)) return null

        var connection: UsbDeviceConnection? = null
        var usbInterface: UsbInterface? = null

        return try {
            connection = manager.openDevice(device) ?: return null
            if (device.interfaceCount > 0) {
                usbInterface = device.getInterface(0)
                connection.claimInterface(usbInterface, true)
            }

            val buffer = ByteArray(64)
            // GET_REPORT for Input Report 0x01
            val requestType = UsbConstants.USB_DIR_IN or
                    UsbConstants.USB_TYPE_CLASS or
                    UsbConstants.USB_INTERFACE_SUBCLASS_BOOT
            val transferred = connection.controlTransfer(
                requestType,
                0x01, // GET_REPORT
                (0x01 shl 8) or 0x01, // Input report 1
                usbInterface?.id ?: 0,
                buffer,
                buffer.size,
                1000
            )

            if (transferred >= 31) {
                // Byte 30 holds DS4 battery data
                DS4Protocol.parseBatteryByte(buffer[30])
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read USB battery", e)
            null
        } finally {
            try {
                if (usbInterface != null && connection != null) {
                    connection.releaseInterface(usbInterface)
                }
                connection?.close()
            } catch (_: Exception) {}
        }
    }
}
