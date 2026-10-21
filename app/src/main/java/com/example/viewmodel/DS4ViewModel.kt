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
import kotlinx.coroutines.flow.StateFlow

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

    // Bluetooth-specific flows
    val bluetoothConnectionState = bluetoothManager.connectionState
    val bluetoothConnectedDevice = bluetoothManager.connectedDevice
    val bluetoothDiscoveredDevices = bluetoothManager.discoveredDevices
    val isBluetoothScanning = bluetoothManager.isScanning

    fun startBluetoothScan() = bluetoothManager.startScan()
    fun stopBluetoothScan() = bluetoothManager.stopScan()
    fun connectBluetooth(device: android.bluetooth.BluetoothDevice) = bluetoothManager.connect(device)

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
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

    fun toggleSimulation(enable: Boolean) {
        repository.toggleSimulationMode(enable)
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return repository.processKeyEvent(event)
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        return repository.processMotionEvent(event)
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DS4ViewModel(app) as T
        }
    }
}
