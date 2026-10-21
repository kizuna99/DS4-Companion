package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.ConnectionStatus
import com.example.model.DS4Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BluetoothManager responsible for:
 * 1. Scanning for DualShock 4 controllers via Bluetooth discovery and bonded devices inspection.
 * 2. Connecting and pairing with DualShock 4 (initiating bond and ACL link).
 * 3. Maintaining connection liveness through real-time hardware ACL broadcast monitoring.
 * 4. Exposing reactive StateFlows to the ViewModel for UI consumption.
 */
class BluetoothManager(private val context: Context) {

    private val TAG = "BluetoothManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val systemBtManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = systemBtManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    // Connection states exposed to ViewModel
    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Bluetooth action received: $action")

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        handleDeviceFound(device)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && isDualShock4Device(device)) {
                        _connectedDevice.value = device
                        _connectionState.value = ConnectionStatus.CONNECTED
                        Log.i(TAG, "DualShock 4 connected: ${device.name ?: device.address}")
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && _connectedDevice.value?.address == device.address) {
                        _connectedDevice.value = null
                        _connectionState.value = ConnectionStatus.DISCONNECTED
                        Log.i(TAG, "DualShock 4 disconnected: ${device.name ?: device.address}")
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (device != null && isDualShock4Device(device)) {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Log.i(TAG, "DualShock 4 successfully bonded: ${device.name}")
                                _connectedDevice.value = device
                                _connectionState.value = ConnectionStatus.CONNECTED
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                _connectionState.value = ConnectionStatus.CONNECTING
                            }
                            BluetoothDevice.BOND_NONE -> {
                                if (_connectedDevice.value?.address == device.address) {
                                    _connectedDevice.value = null
                                    _connectionState.value = ConnectionStatus.DISCONNECTED
                                }
                            }
                        }
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        _connectedDevice.value = null
                        _connectionState.value = ConnectionStatus.DISCONNECTED
                        _isScanning.value = false
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        checkBondedDevices()
                    }
                }
            }
        }
    }

    init {
        registerReceiver()
        checkBondedDevices()
    }

    /**
     * Registers the broadcast receiver for discovery and ACL connection events.
     */
    fun registerReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth broadcast receiver", e)
        }
    }

    /**
     * Unregisters the broadcast receiver to prevent memory leaks.
     */
    fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
            isReceiverRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Bluetooth broadcast receiver", e)
        }
    }

    /**
     * Scans for paired/bonded DualShock 4 devices.
     */
    @SuppressLint("MissingPermission")
    fun checkBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()

        return try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val ds4List = bonded.filter { isDualShock4Device(it) }

            _discoveredDevices.value = ds4List

            if (ds4List.isNotEmpty() && _connectedDevice.value == null) {
                // If a paired DS4 is present, set as candidate
                val active = ds4List.first()
                _connectedDevice.value = active
                _connectionState.value = ConnectionStatus.CONNECTED
            }
            ds4List
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth connect permission missing", e)
            emptyList()
        }
    }

    /**
     * Starts active Bluetooth discovery to scan for nearby controllers.
     */
    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!hasScanPermission()) return false

        return try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            // Populate bonded first
            checkBondedDevices()
            val started = adapter.startDiscovery()
            _isScanning.value = started
            started
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth scan permission", e)
            false
        }
    }

    /**
     * Stops ongoing Bluetooth discovery.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = bluetoothAdapter ?: return
        if (!hasScanPermission()) return

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            _isScanning.value = false
        } catch (e: SecurityException) {
            Log.e(TAG, "Error cancelling Bluetooth discovery", e)
        }
    }

    /**
     * Connects to a specific DualShock 4 controller.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasConnectPermission()) return

        scope.launch {
            _connectionState.value = ConnectionStatus.CONNECTING
            stopScan()

            try {
                if (device.bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "Initiating pairing bond with ${device.name ?: device.address}")
                    device.createBond()
                } else {
                    _connectedDevice.value = device
                    _connectionState.value = ConnectionStatus.CONNECTED
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error connecting to Bluetooth device", e)
                _connectionState.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    /**
     * Disconnects from current controller.
     */
    fun disconnect() {
        _connectedDevice.value = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
        stopScan()
    }

    /**
     * Releases resources when destroyed.
     */
    fun release() {
        stopScan()
        unregisterReceiver()
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceFound(device: BluetoothDevice) {
        if (!hasConnectPermission()) return
        try {
            val name = device.name ?: ""
            if (isDualShock4Device(device)) {
                val currentList = _discoveredDevices.value
                if (currentList.none { it.address == device.address }) {
                    _discoveredDevices.value = currentList + device
                    Log.d(TAG, "Discovered DualShock 4: $name (${device.address})")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error examining found device", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun isDualShock4Device(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission()) return false
        return try {
            val name = device.name?.lowercase() ?: ""
            name.contains("wireless controller") ||
                    name.contains("dualshock") ||
                    name.contains("cuh-zct") ||
                    (name.contains("sony") && name.contains("controller"))
        } catch (e: SecurityException) {
            false
        }
    }

    fun hasConnectPermission(): Boolean {
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

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
