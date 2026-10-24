package com.example.viewmodel

import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.DS4InputRepository
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.protocol.MotorSelection
import com.example.protocol.VibrationController
import com.example.protocol.VibrationTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DS4ViewModel(application: Application) : AndroidViewModel(application) {

    val repository = DS4InputRepository(application.applicationContext)
    val controllerManager = repository.controllerManager
    val batteryRepository = controllerManager.batteryRepository
    val bluetoothManager = com.example.bluetooth.BluetoothManager(application.applicationContext)

    val connectionStatus = repository.connectionStatus
    val currentDevice = repository.currentDevice
    val discoveredDevices = repository.discoveredDevices
    val lightBarColor = repository.lightBarColor
    val lightBarResult = repository.lightBarResult
    val inputState = repository.inputState
    val diagnosticsState = repository.diagnosticsState
    val isSimulationMode = repository.isSimulationMode
    val batteryState = repository.batteryState

    // Bluetooth-specific flows
    val bluetoothConnectionState = bluetoothManager.connectionState
    val bluetoothConnectedDevice = bluetoothManager.connectedDevice
    val bluetoothDiscoveredDevices = bluetoothManager.discoveredDevices
    val isBluetoothScanning = bluetoothManager.isScanning

    fun startBluetoothScan() = bluetoothManager.startScan()
    fun stopBluetoothScan() = bluetoothManager.stopScan()
    fun connectBluetooth(device: android.bluetooth.BluetoothDevice) = bluetoothManager.connect(device)

    // Vibration controller
    val vibrationController = VibrationController(application.applicationContext)
    private val _vibrationResult = MutableStateFlow<VibrationTestResult?>(null)
    val vibrationResult: StateFlow<VibrationTestResult?> = _vibrationResult.asStateFlow()

    val isVibrating: StateFlow<Boolean> = vibrationController.isVibrating

    private val _isVibrationEnabled = MutableStateFlow(true)
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    fun toggleVibrationEnabled() {
        _isVibrationEnabled.value = !_isVibrationEnabled.value
        if (!_isVibrationEnabled.value) {
            stopVibration()
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        if (!enabled) {
            stopVibration()
        }
    }

    fun testVibration(
        durationMs: Long = 1000L,
        motor: MotorSelection = MotorSelection.BOTH
    ) {
        if (!_isVibrationEnabled.value) {
            _vibrationResult.value = VibrationTestResult(
                false,
                "Vibration is currently disabled. Toggle the Vibration switch ON to test rumble."
            )
            return
        }
        val device = currentDevice.value
        val color = lightBarColor.value
        val result = vibrationController.testVibration(durationMs, motor, device, color)
        _vibrationResult.value = result
    }

    fun stopVibration() {
        val device = currentDevice.value
        val color = lightBarColor.value
        val result = vibrationController.stopVibration(device, color)
        _vibrationResult.value = result
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
        vibrationController.stopAll()
        repository.motionSensorManager.release()
    }

    fun refresh() {
        repository.refreshDevices()
    }

    fun connect(device: DS4DeviceInfo) {
        repository.connectDevice(device)
    }

    fun disconnect() {
        repository.disconnectDevice()
    }

    fun applyColor(color: LightBarColor) {
        repository.applyLightBarColor(color)
    }

    fun resetColor() {
        repository.resetLightBarColor()
    }

    fun resetInputTester() {
        repository.resetInputTester()
    }

    fun clearDiagnostics() {
        repository.clearDiagnostics()
    }

    fun setSimulationMode(enabled: Boolean) {
        repository.setSimulationMode(enabled)
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return repository.processKeyEvent(event)
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        return repository.processMotionEvent(event)
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        return repository.processTouchEvent(event)
    }

    fun toggleSimulation(enabled: Boolean? = null) {
        val target = enabled ?: !repository.isSimulationMode.value
        repository.setSimulationMode(target)
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = DS4ViewModelFactory(application)
    }
}

class DS4ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DS4ViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DS4ViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
