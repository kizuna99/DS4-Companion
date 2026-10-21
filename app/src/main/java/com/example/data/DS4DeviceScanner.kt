package com.example.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.DS4Feature
import com.example.model.TransportType
import com.example.protocol.DS4Protocol
import com.example.protocol.DS4UsbTransmitter

class DS4DeviceScanner(private val context: Context) {

    private val TAG = "DS4DeviceScanner"
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val usbTransmitter = DS4UsbTransmitter(context)

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Scans all system input devices, USB devices, and paired Bluetooth devices.
     */
    fun scanDevices(): List<DS4DeviceInfo> {
        val list = mutableListOf<DS4DeviceInfo>()

        // 1. Scan InputDevice framework (actively connected controllers providing HID input)
        scanInputDevices(list)

        // 2. Scan USB OTG devices
        scanUsbDevices(list)

        // 3. Scan paired Bluetooth devices (if not already found in active input devices)
        scanBluetoothDevices(list)

        return list
    }

    private fun scanInputDevices(outList: MutableList<DS4DeviceInfo>) {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources

            val isGamepad = DS4Constants.isGamepadOrJoystick(sources)
            val isDs4 = DS4Constants.isDualShock4(device.vendorId, device.productId, device.name)

            if (isDs4 || (isGamepad && device.name.lowercase().contains("controller"))) {
                val isSony = device.vendorId == DS4Constants.SONY_VENDOR_ID ||
                        device.name.lowercase().contains("wireless controller") ||
                        device.name.lowercase().contains("dualshock")

                val modelName = when (device.productId) {
                    DS4Constants.SONY_DS4_V1_PID -> "DualShock 4 V1 (CUH-ZCT1)"
                    DS4Constants.SONY_DS4_V2_PID -> "DualShock 4 V2 (CUH-ZCT2)"
                    DS4Constants.SONY_DS4_DONGLE_PID -> "DualShock 4 Wireless Adapter"
                    else -> if (isSony) "Sony DualShock 4" else device.name
                }

                // Determine transport
                val isUsbAttached = usbTransmitter.findAttachedDs4UsbDevice() != null
                val transport = if (isUsbAttached) {
                    TransportType.USB_OTG
                } else {
                    TransportType.BLUETOOTH
                }

                // Battery detection
                val batteryInfo = detectBatteryForInputDevice(device, isUsbAttached)

                val features = mutableListOf(
                    DS4Feature.BUTTON_INPUT,
                    DS4Feature.ANALOG_STICKS,
                    DS4Feature.ANALOG_TRIGGERS,
                    DS4Feature.TOUCHPAD
                )
                if (transport == TransportType.USB_OTG) {
                    features.add(DS4Feature.LIGHTBAR_USB)
                } else {
                    features.add(DS4Feature.LIGHTBAR_BT)
                }
                if (batteryInfo.status != BatteryStatus.UNAVAILABLE) {
                    features.add(DS4Feature.BATTERY_READING)
                }

                outList.add(
                    DS4DeviceInfo(
                        id = "input_${device.id}",
                        name = device.name,
                        transport = transport,
                        vendorId = if (device.vendorId != 0) device.vendorId else DS4Constants.SONY_VENDOR_ID,
                        productId = if (device.productId != 0) device.productId else DS4Constants.SONY_DS4_V2_PID,
                        inputDeviceId = device.id,
                        bluetoothAddress = null,
                        modelName = modelName,
                        isOfficialSony = isSony,
                        batteryInfo = batteryInfo,
                        supportedFeatures = features
                    )
                )
            }
        }
    }

    private fun detectBatteryForInputDevice(device: InputDevice, isUsbAttached: Boolean): BatteryInfo {
        // Try USB hardware report first if connected via USB
        if (isUsbAttached) {
            val usbBattery = usbTransmitter.readBatteryOverUsb()
            if (usbBattery != null && usbBattery.status != BatteryStatus.UNAVAILABLE) {
                return usbBattery
            }
        }

        // Try Android 12+ (API 31+) InputDevice BatteryState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val batteryState = device.batteryState
                val capacity = batteryState.capacity
                val status = batteryState.status
                val isCharging = status == 2 // BatteryState.STATUS_CHARGING

                if (!capacity.isNaN() && capacity >= 0f) {
                    return DS4Protocol.parseAndroidBatteryState(capacity, isCharging)
                }
            } catch (e: Throwable) {
                Log.d(TAG, "BatteryState not supported on this device/kernel", e)
            }
        }

        // Return honest unavailable rather than synthetic data
        return BatteryInfo(
            percentage = null,
            status = BatteryStatus.UNAVAILABLE,
            isCharging = false,
            sourceDescription = "OS driver does not report battery over current transport"
        )
    }

    private fun scanUsbDevices(outList: MutableList<DS4DeviceInfo>) {
        val usbDevice = usbTransmitter.findAttachedDs4UsbDevice() ?: return
        // Check if already mapped through input devices
        val alreadyMapped = outList.any { it.vendorId == usbDevice.vendorId && it.productId == usbDevice.productId }
        if (!alreadyMapped) {
            val modelName = when (usbDevice.productId) {
                DS4Constants.SONY_DS4_V1_PID -> "DualShock 4 V1 (USB OTG)"
                DS4Constants.SONY_DS4_V2_PID -> "DualShock 4 V2 (USB OTG)"
                else -> "Sony DualShock 4 (USB OTG)"
            }
            val usbBattery = usbTransmitter.readBatteryOverUsb() ?: BatteryInfo(
                percentage = null,
                status = BatteryStatus.UNAVAILABLE,
                isCharging = true,
                sourceDescription = "USB connected (charging)"
            )

            outList.add(
                DS4DeviceInfo(
                    id = "usb_${usbDevice.deviceId}",
                    name = usbDevice.productName ?: "Sony DualShock 4",
                    transport = TransportType.USB_OTG,
                    vendorId = usbDevice.vendorId,
                    productId = usbDevice.productId,
                    inputDeviceId = null,
                    modelName = modelName,
                    isOfficialSony = true,
                    batteryInfo = usbBattery,
                    supportedFeatures = listOf(
                        DS4Feature.BUTTON_INPUT,
                        DS4Feature.ANALOG_STICKS,
                        DS4Feature.ANALOG_TRIGGERS,
                        DS4Feature.LIGHTBAR_USB
                    )
                )
            )
        }
    }

    private fun scanBluetoothDevices(outList: MutableList<DS4DeviceInfo>) {
        if (!hasBluetoothPermission()) return

        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return
            val pairedDevices: Set<BluetoothDevice>? = btAdapter.bondedDevices

            pairedDevices?.forEach { device ->
                val deviceName = device.name ?: ""
                val lower = deviceName.lowercase()
                if (lower.contains("wireless controller") || lower.contains("dualshock")) {
                    val alreadyInList = outList.any { it.name.equals(deviceName, ignoreCase = true) }
                    if (!alreadyInList) {
                        outList.add(
                            DS4DeviceInfo(
                                id = "bt_${device.address}",
                                name = deviceName,
                                transport = TransportType.BLUETOOTH,
                                vendorId = DS4Constants.SONY_VENDOR_ID,
                                productId = DS4Constants.SONY_DS4_V2_PID,
                                bluetoothAddress = device.address,
                                modelName = "DualShock 4 (Paired Bluetooth)",
                                isOfficialSony = true,
                                batteryInfo = BatteryInfo(
                                    percentage = null,
                                    status = BatteryStatus.UNAVAILABLE,
                                    isCharging = false,
                                    sourceDescription = "Connect controller to query live battery"
                                ),
                                supportedFeatures = listOf(
                                    DS4Feature.BUTTON_INPUT,
                                    DS4Feature.ANALOG_STICKS,
                                    DS4Feature.ANALOG_TRIGGERS,
                                    DS4Feature.LIGHTBAR_BT
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission missing", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Bluetooth paired devices", e)
        }
    }
}
