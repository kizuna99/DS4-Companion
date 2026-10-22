package com.example.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.ConnectionStatus
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.DS4Feature
import com.example.model.TransportType
import com.example.protocol.DS4Protocol
import com.example.protocol.DS4UsbTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ControllerManager repository class in Kotlin.
 *
 * Responsibilities:
 * 1. Manages real-time detection of connected controllers via [InputManager.InputDeviceListener].
 * 2. Maintains [StateFlow] of the current connection status ([ConnectionStatus]), active controller, and discovered controllers.
 * 3. Provides explicit methods for scanning hardware gamepads via [InputDevice.getDeviceIds].
 * 4. Lifecycle-aware via [DefaultLifecycleObserver] and [bindLifecycle] to prevent memory leaks and handle background/foreground transitions.
 * 5. Adheres to Android MVVM architectural standards.
 */
class ControllerManager(
    private val context: Context
) : InputManager.InputDeviceListener, DefaultLifecycleObserver {

    private val TAG = "ControllerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val usbTransmitter = DS4UsbTransmitter(context)
    val batteryRepository = BatteryRepository()

    // StateFlows for connection management
    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _activeController = MutableStateFlow<DS4DeviceInfo?>(null)
    val activeController: StateFlow<DS4DeviceInfo?> = _activeController.asStateFlow()

    private val _discoveredControllers = MutableStateFlow<List<DS4DeviceInfo>>(emptyList())
    val discoveredControllers: StateFlow<List<DS4DeviceInfo>> = _discoveredControllers.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private var isRegistered = false
    private var boundLifecycle: Lifecycle? = null
    private var batteryPollingJob: Job? = null

    // BroadcastReceiver for USB OTG and Bluetooth ACL connection events
    private val hardwareBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Hardware event received: $action")
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    scanNow()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    handleUsbDetached()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    scanNow()
                }
            }
        }
    }

    init {
        startMonitoring()
        val initialDevices = scanInputDeviceIds()
        if (initialDevices.isNotEmpty()) {
            val initial = initialDevices.first()
            _activeController.value = initial
            _connectionState.value = ConnectionStatus.CONNECTED
            batteryRepository.updateBattery(initial.batteryInfo)
            startBatteryPolling()
        } else {
            _activeController.value = null
            _connectionState.value = ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Binds this repository to an Android [Lifecycle] (e.g. Activity or Fragment)
     * to make it fully lifecycle-aware.
     */
    fun bindLifecycle(lifecycle: Lifecycle) {
        boundLifecycle?.removeObserver(this)
        boundLifecycle = lifecycle
        lifecycle.addObserver(this)
    }

    // --- Lifecycle Callbacks (DefaultLifecycleObserver) ---

    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle onStart: starting monitoring and refreshing controllers")
        startMonitoring()
        scanNow()
        if (_activeController.value != null && !_isSimulationMode.value) {
            startBatteryPolling()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle onStop: pausing battery polling")
        stopBatteryPolling()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle onDestroy: releasing listeners")
        stopMonitoring()
        stopBatteryPolling()
        boundLifecycle?.removeObserver(this)
        boundLifecycle = null
    }

    // --- InputManager.InputDeviceListener Implementation ---

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(TAG, "InputManager: onInputDeviceAdded(deviceId = $deviceId)")
        val device = InputDevice.getDevice(deviceId) ?: return
        if (DS4Constants.isDualShock4(device.vendorId, device.productId, device.name) ||
            DS4Constants.isGamepadOrJoystick(device.sources)
        ) {
            scanNow()
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(TAG, "InputManager: onInputDeviceRemoved(deviceId = $deviceId)")
        val current = _activeController.value
        if (current?.inputDeviceId == deviceId) {
            scanNow()
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d(TAG, "InputManager: onInputDeviceChanged(deviceId = $deviceId)")
        scanNow()
    }

    // --- Registration & Monitoring Lifecycle ---

    /**
     * Registers the InputDeviceListener on InputManager and registers the hardware BroadcastReceiver.
     */
    fun startMonitoring() {
        if (isRegistered) return
        try {
            inputManager?.registerInputDeviceListener(this, mainHandler)

            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(hardwareBroadcastReceiver, filter)
            isRegistered = true
            Log.i(TAG, "InputManager and hardware monitoring registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register controller monitoring", e)
        }
    }

    /**
     * Unregisters all hardware listeners to prevent leaks.
     */
    fun stopMonitoring() {
        stopBatteryPolling()
        if (!isRegistered) return
        try {
            inputManager?.unregisterInputDeviceListener(this)
            context.unregisterReceiver(hardwareBroadcastReceiver)
            isRegistered = false
            Log.i(TAG, "Controller monitoring unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister controller monitoring", e)
        }
    }

    // --- Scanning Methods using InputDevice.getDeviceIds() ---

    /**
     * Scans currently connected gamepads and joysticks specifically via [InputDevice.getDeviceIds].
     *
     * Iterates over all device IDs reported by the Android input subsystem,
     * queries [InputDevice.getDevice], filters for gamepad/joystick input sources,
     * identifies DualShock 4 hardware signatures (VID 0x054C, PIDs 0x05C4, 0x09CC, 0x0BA0),
     * and queries Android 12+ battery state.
     */
    fun scanInputDeviceIds(): List<DS4DeviceInfo> {
        val detected = mutableListOf<DS4DeviceInfo>()
        val deviceIds = InputDevice.getDeviceIds()

        for (id in deviceIds) {
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

                val isUsbAttached = usbTransmitter.findAttachedDs4UsbDevice() != null
                val transport = if (isUsbAttached) TransportType.USB_OTG else TransportType.BLUETOOTH
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

                detected.add(
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
        return detected
    }

    /**
     * Executes immediate scanning across Android InputDevice subsystem, USB OTG, and Bluetooth HID,
     * updating [connectionState], [activeController], and [discoveredControllers].
     *
     * Only actively connected controllers (present in InputDevice or UsbManager) trigger
     * [ConnectionStatus.CONNECTED]. Paired/bonded Bluetooth devices that are offline do not.
     */
    fun scanNow() {
        scope.launch {
            if (_isSimulationMode.value) return@launch

            _connectionState.value = ConnectionStatus.CONNECTING
            val activeDevices = mutableListOf<DS4DeviceInfo>()
            val pairedOfflineDevices = mutableListOf<DS4DeviceInfo>()

            // 1. Scan active hardware InputDevices (gamepads actively connected via BT or USB)
            activeDevices.addAll(scanInputDeviceIds())

            // 2. Scan physically attached USB OTG devices
            scanUsbOtgDevices(activeDevices)

            // 3. Scan paired Bluetooth devices (kept as paired list, not marked as actively connected)
            scanBluetoothPairedDevices(pairedOfflineDevices, activeDevices)

            _discoveredControllers.value = activeDevices + pairedOfflineDevices

            if (activeDevices.isNotEmpty()) {
                val current = _activeController.value
                val stillConnected = current?.let { active ->
                    activeDevices.find { it.id == active.id || (it.vendorId == active.vendorId && it.productId == active.productId) }
                }
                val selected = stillConnected ?: activeDevices.first()
                _activeController.value = selected
                _connectionState.value = ConnectionStatus.CONNECTED
                batteryRepository.updateBattery(selected.batteryInfo)
                startBatteryPolling()
            } else {
                _activeController.value = null
                _connectionState.value = ConnectionStatus.DISCONNECTED
                batteryRepository.setUnavailable("No controller connected")
                stopBatteryPolling()
            }
        }
    }

    /**
     * Handles USB detached broadcast specifically to distinguish charger disconnection
     * from controller disconnection.
     */
    private fun handleUsbDetached() {
        scope.launch {
            Log.d(TAG, "Handling USB detached event. Checking remaining active connections...")
            val current = _activeController.value
            val activeDevices = scanInputDeviceIds()

            if (activeDevices.isNotEmpty()) {
                // Controller remains active (e.g., via Bluetooth while USB charging stopped)
                val matching = current?.let { active ->
                    activeDevices.find { it.id == active.id || (it.vendorId == active.vendorId && it.productId == active.productId) }
                } ?: activeDevices.first()
                _activeController.value = matching
                _connectionState.value = ConnectionStatus.CONNECTED
                // Refresh battery immediately to clear charging flag without requiring manual scan
                refreshBatteryTelemetry(matching)
            } else if (current?.transport == TransportType.USB_OTG) {
                // Controller was connected solely via USB OTG cable and is now disconnected
                _activeController.value = null
                _connectionState.value = ConnectionStatus.DISCONNECTED
                batteryRepository.setUnavailable("Controller disconnected (USB OTG detached)")
                stopBatteryPolling()
            } else if (current != null) {
                // USB charger disconnected, controller was on Bluetooth
                refreshBatteryTelemetry(current)
            }
        }
    }

    /**
     * Connects to a specific discovered controller.
     */
    fun connect(device: DS4DeviceInfo) {
        _activeController.value = device
        _connectionState.value = ConnectionStatus.CONNECTED
        batteryRepository.updateBattery(device.batteryInfo)
        startBatteryPolling()
    }

    /**
     * Disconnects current controller.
     */
    fun disconnect() {
        stopBatteryPolling()
        _activeController.value = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
        _isSimulationMode.value = false
        batteryRepository.setUnavailable("Controller disconnected")
    }

    /**
     * Toggles device simulation mode.
     */
    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            stopBatteryPolling()
            val simBattery = batteryRepository.setSimulatedBattery(85, false)
            val simDevice = DS4DeviceInfo(
                id = "sim_ds4_01",
                name = "DualShock 4 [Safety Simulation]",
                transport = TransportType.SIMULATION,
                vendorId = DS4Constants.SONY_VENDOR_ID,
                productId = DS4Constants.SONY_DS4_V2_PID,
                modelName = "Sony DualShock 4 V2 (Simulated)",
                isOfficialSony = true,
                batteryInfo = simBattery,
                supportedFeatures = listOf(
                    DS4Feature.BUTTON_INPUT,
                    DS4Feature.ANALOG_STICKS,
                    DS4Feature.ANALOG_TRIGGERS,
                    DS4Feature.LIGHTBAR_USB,
                    DS4Feature.LIGHTBAR_BT,
                    DS4Feature.BATTERY_READING,
                    DS4Feature.TOUCHPAD,
                    DS4Feature.RUMBLE
                )
            )
            _activeController.value = simDevice
            _connectionState.value = ConnectionStatus.CONNECTED
        } else {
            scanNow()
        }
    }

    /**
     * Starts background polling for battery telemetry (2-second interval)
     * while a physical controller is connected.
     */
    fun startBatteryPolling() {
        if (batteryPollingJob?.isActive == true) return
        batteryPollingJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val controller = _activeController.value
                if (controller == null || _connectionState.value != ConnectionStatus.CONNECTED) {
                    break
                }
                if (_isSimulationMode.value) {
                    continue
                }
                refreshBatteryTelemetry(controller)
            }
        }
    }

    /**
     * Cancels active battery polling job to conserve resources.
     */
    fun stopBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = null
    }

    /**
     * Reads fresh battery telemetry from hardware over USB or Android InputDevice API,
     * updates the authoritative [BatteryRepository] and synchronizes [_activeController].
     */
    fun refreshBatteryTelemetry(controller: DS4DeviceInfo? = _activeController.value) {
        if (controller == null || _isSimulationMode.value) return

        val isUsbAttached = usbTransmitter.findAttachedDs4UsbDevice() != null
        var freshInfo: BatteryInfo? = null

        // 1. Check direct USB telemetry if attached
        if (isUsbAttached || controller.transport == TransportType.USB_OTG) {
            val usbBattery = usbTransmitter.readBatteryOverUsb()
            if (usbBattery != null && usbBattery.status != BatteryStatus.UNAVAILABLE) {
                freshInfo = usbBattery
            }
        }

        // 2. Query Android InputDevice framework
        if (freshInfo == null && controller.inputDeviceId != null) {
            val device = InputDevice.getDevice(controller.inputDeviceId)
            if (device != null) {
                val inputBattery = batteryRepository.extractFromInputDevice(device)
                if (inputBattery.status != BatteryStatus.UNAVAILABLE || inputBattery.isCharging != null || inputBattery.percentage != null) {
                    freshInfo = inputBattery
                }
            }
        }

        if (freshInfo != null) {
            batteryRepository.updateBattery(freshInfo)
            _activeController.value = _activeController.value?.copy(batteryInfo = freshInfo)
        } else {
            // Check if telemetry is older than 15s and mark stale if necessary
            val staleInfo = batteryRepository.markStaleIfOutdated(15_000L)
            if (staleInfo.isStale && _activeController.value?.batteryInfo != staleInfo) {
                _activeController.value = _activeController.value?.copy(batteryInfo = staleInfo)
            }
        }
    }

    private fun detectBatteryForInputDevice(device: InputDevice, isUsbAttached: Boolean): BatteryInfo {
        if (isUsbAttached) {
            val usbBattery = usbTransmitter.readBatteryOverUsb()
            if (usbBattery != null && usbBattery.status != BatteryStatus.UNAVAILABLE) {
                return usbBattery
            }
        }
        return batteryRepository.extractFromInputDevice(device)
    }

    private fun scanUsbOtgDevices(outList: MutableList<DS4DeviceInfo>) {
        val usbDevice = usbTransmitter.findAttachedDs4UsbDevice() ?: return
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

    private fun scanBluetoothPairedDevices(
        outList: MutableList<DS4DeviceInfo>,
        activeList: List<DS4DeviceInfo>
    ) {
        if (!hasBluetoothPermission()) return

        try {
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return
            val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices

            pairedDevices?.forEach { device ->
                val deviceName = device.name ?: ""
                val lower = deviceName.lowercase()
                if (lower.contains("wireless controller") || lower.contains("dualshock")) {
                    val alreadyActive = activeList.any { it.name.equals(deviceName, ignoreCase = true) }
                    val alreadyInList = outList.any { it.name.equals(deviceName, ignoreCase = true) }
                    if (!alreadyActive && !alreadyInList) {
                        outList.add(
                            DS4DeviceInfo(
                                id = "bt_${device.address}",
                                name = deviceName,
                                transport = TransportType.BLUETOOTH,
                                vendorId = DS4Constants.SONY_VENDOR_ID,
                                productId = DS4Constants.SONY_DS4_V2_PID,
                                bluetoothAddress = device.address,
                                modelName = "DualShock 4 (Paired/Offline)",
                                isOfficialSony = true,
                                batteryInfo = BatteryInfo(
                                    percentage = null,
                                    status = BatteryStatus.UNAVAILABLE,
                                    isCharging = false,
                                    sourceDescription = "Controller is not connected"
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
}
