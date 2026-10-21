package com.example.model

enum class TransportType(val displayName: String) {
    BLUETOOTH("Bluetooth"),
    USB_OTG("USB OTG"),
    SYSTEM_INPUT_DEVICE("Android System Input"),
    SIMULATION("Device Simulation Mode")
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class BatteryStatus(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    EMPTY("Empty"),
    CHARGING("Charging"),
    UNAVAILABLE("Battery unavailable")
}

data class BatteryInfo(
    val percentage: Int? = null,
    val status: BatteryStatus = BatteryStatus.UNAVAILABLE,
    val isCharging: Boolean = false,
    val sourceDescription: String = "No data received"
)

enum class DS4Feature(val label: String) {
    BUTTON_INPUT("Button Input Events"),
    ANALOG_STICKS("Analog Stick Telemetry"),
    ANALOG_TRIGGERS("L2/R2 Analog Pressure"),
    LIGHTBAR_USB("RGB Light Bar Control (USB OTG)"),
    LIGHTBAR_BT("RGB Light Bar (Bluetooth)"),
    BATTERY_READING("Hardware Battery Level"),
    TOUCHPAD("Touchpad Events"),
    RUMBLE("Vibration / Haptics")
}

data class DS4DeviceInfo(
    val id: String,
    val name: String,
    val transport: TransportType,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val inputDeviceId: Int? = null,
    val bluetoothAddress: String? = null,
    val modelName: String = "Sony DualShock 4",
    val isOfficialSony: Boolean = true,
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val supportedFeatures: List<DS4Feature> = listOf(
        DS4Feature.BUTTON_INPUT,
        DS4Feature.ANALOG_STICKS,
        DS4Feature.ANALOG_TRIGGERS
    ),
    val connectedTimestamp: Long = System.currentTimeMillis()
)
