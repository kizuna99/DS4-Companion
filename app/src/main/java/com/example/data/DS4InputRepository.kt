package com.example.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.model.AxisRangeInfo
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.CenteringStatus
import com.example.model.ConnectionStatus
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.DS4Feature
import com.example.model.DiagnosticsState
import com.example.model.InputLogEvent
import com.example.model.InputState
import com.example.model.LightBarApplyResult
import com.example.model.LightBarColor
import com.example.model.TransportType
import com.example.protocol.DS4UsbTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class DS4InputRepository(private val context: Context) {

    private val TAG = "DS4InputRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val controllerManager = ControllerManager(context)
    val lightBarController = com.example.protocol.LightBarController(context)
    private val usbTransmitter = DS4UsbTransmitter(context)

    // Connection states directly delegated to ControllerManager
    val connectionStatus: StateFlow<ConnectionStatus> = controllerManager.connectionState
    val currentDevice: StateFlow<DS4DeviceInfo?> = controllerManager.activeController
    val discoveredDevices: StateFlow<List<DS4DeviceInfo>> = controllerManager.discoveredControllers
    val isSimulationMode: StateFlow<Boolean> = controllerManager.isSimulationMode

    // Light bar state
    private val _lightBarColor = MutableStateFlow(LightBarColor.PLAYSTATION_BLUE)
    val lightBarColor: StateFlow<LightBarColor> = _lightBarColor.asStateFlow()

    private val _lightBarResult = MutableStateFlow<LightBarApplyResult?>(null)
    val lightBarResult: StateFlow<LightBarApplyResult?> = _lightBarResult.asStateFlow()

    // Input tester state
    private val _inputState = MutableStateFlow(InputState())
    val inputState: StateFlow<InputState> = _inputState.asStateFlow()

    // Diagnostics state
    private val _diagnosticsState = MutableStateFlow(DiagnosticsState())
    val diagnosticsState: StateFlow<DiagnosticsState> = _diagnosticsState.asStateFlow()

    // Track button press start times for stuck button detection
    private val buttonPressStartTimes = mutableMapOf<String, Long>()

    init {
        scope.launch {
            controllerManager.activeController.collect { device ->
                if (device != null) {
                    inspectDeviceDiagnostics(device)
                }
            }
        }
    }

    fun refreshDevices() {
        controllerManager.scanNow()
    }

    fun connectDevice(device: DS4DeviceInfo) {
        controllerManager.connect(device)
        inspectDeviceDiagnostics(device)
    }

    fun disconnectDevice() {
        controllerManager.disconnect()
    }

    fun toggleSimulationMode(enable: Boolean) {
        controllerManager.setSimulationMode(enable)
    }

    private fun inspectDeviceDiagnostics(device: DS4DeviceInfo) {
        val ranges = mutableListOf<AxisRangeInfo>()
        val devId = device.inputDeviceId
        var sourcesDesc = "System HID Gamepad"

        if (devId != null) {
            val inputDev = InputDevice.getDevice(devId)
            if (inputDev != null) {
                sourcesDesc = "Sources: ${Integer.toHexString(inputDev.sources)}"
                val axes = listOf(
                    "AXIS_X" to MotionEvent.AXIS_X,
                    "AXIS_Y" to MotionEvent.AXIS_Y,
                    "AXIS_Z" to MotionEvent.AXIS_Z,
                    "AXIS_RZ" to MotionEvent.AXIS_RZ,
                    "AXIS_LTRIGGER" to MotionEvent.AXIS_LTRIGGER,
                    "AXIS_RTRIGGER" to MotionEvent.AXIS_RTRIGGER,
                    "AXIS_HAT_X" to MotionEvent.AXIS_HAT_X,
                    "AXIS_HAT_Y" to MotionEvent.AXIS_HAT_Y
                )
                for ((name, axisId) in axes) {
                    val range = inputDev.getMotionRange(axisId)
                    if (range != null) {
                        ranges.add(
                            AxisRangeInfo(
                                axisName = name,
                                axisId = axisId,
                                min = range.min,
                                max = range.max,
                                flat = range.flat,
                                fuzz = range.fuzz
                            )
                        )
                    }
                }
            }
        }

        val notes = mutableListOf<String>()
        if (device.transport == TransportType.USB_OTG) {
            notes.add("USB OTG connection active: Direct hardware HID Output Reports (Report 0x05) supported for instantaneous light bar and rumble.")
        } else if (device.transport == TransportType.BLUETOOTH) {
            notes.add("Bluetooth connection active: Standard Android OS isolates HID output socket. For hardware light bar color programming, use a USB OTG adapter.")
        } else if (device.transport == TransportType.SIMULATION) {
            notes.add("Safety Simulation Mode: Ideal for testing UI, button telemetry, and diagnostics without physical controller hardware.")
        }

        _diagnosticsState.update {
            it.copy(
                vendorIdHex = device.vendorId?.let { v -> "0x${Integer.toHexString(v).uppercase()}" } ?: "-",
                productIdHex = device.productId?.let { p -> "0x${Integer.toHexString(p).uppercase()}" } ?: "-",
                inputSourcesDescription = sourcesDesc,
                axisRanges = ranges,
                hardwareNotes = notes
            )
        }
    }

    fun applyLightBarColor(color: LightBarColor) {
        _lightBarColor.value = color
        val device = currentDevice.value
        val result = lightBarController.applyColor(color, device)
        _lightBarResult.value = result
    }

    fun resetLightBarColor() {
        applyLightBarColor(LightBarColor.PLAYSTATION_BLUE)
    }

    fun resetInputTester() {
        _inputState.update {
            InputState(
                touchedInputs = emptySet(),
                recentEvents = emptyList()
            )
        }
        buttonPressStartTimes.clear()
        _diagnosticsState.update {
            it.copy(stuckButtons = emptySet(), detectedInputs = emptySet())
        }
    }

    fun clearDiagnostics() {
        _diagnosticsState.update {
            it.copy(
                detectedInputs = emptySet(),
                stuckButtons = emptySet(),
                totalEventsCount = 0L,
                leftStickDriftOffset = 0f,
                rightStickDriftOffset = 0f,
                leftStickCenteringStatus = CenteringStatus.NEUTRAL,
                rightStickCenteringStatus = CenteringStatus.NEUTRAL
            )
        }
    }

    /**
     * Dispatched from Activity's dispatchKeyEvent
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val keyCode = event.keyCode
        val buttonName = getButtonName(keyCode) ?: return false

        // Update stuck button tracking
        val now = SystemClock.uptimeMillis()
        if (isDown) {
            buttonPressStartTimes.putIfAbsent(buttonName, now)
        } else {
            buttonPressStartTimes.remove(buttonName)
        }

        // Check for stuck buttons (held > 12 seconds continuously)
        val stuck = buttonPressStartTimes.filter { now - it.value > 12000 }.keys.toSet()

        _inputState.update { current ->
            val updated = when (keyCode) {
                DS4Constants.KEY_CROSS -> current.copy(buttonCross = isDown)
                DS4Constants.KEY_CIRCLE -> current.copy(buttonCircle = isDown)
                DS4Constants.KEY_SQUARE -> current.copy(buttonSquare = isDown)
                DS4Constants.KEY_TRIANGLE -> current.copy(buttonTriangle = isDown)
                DS4Constants.KEY_L1 -> current.copy(buttonL1 = isDown)
                DS4Constants.KEY_R1 -> current.copy(buttonR1 = isDown)
                DS4Constants.KEY_L2 -> current.copy(
                    buttonL2 = isDown,
                    l2Pressure = if (isDown && current.l2Pressure == 0f) 1f else current.l2Pressure
                )
                DS4Constants.KEY_R2 -> current.copy(
                    buttonR2 = isDown,
                    r2Pressure = if (isDown && current.r2Pressure == 0f) 1f else current.r2Pressure
                )
                DS4Constants.KEY_L3 -> current.copy(buttonL3 = isDown)
                DS4Constants.KEY_R3 -> current.copy(buttonR3 = isDown)
                DS4Constants.KEY_SHARE -> current.copy(buttonShare = isDown)
                DS4Constants.KEY_OPTIONS -> current.copy(buttonOptions = isDown)
                DS4Constants.KEY_PS -> current.copy(buttonPs = isDown)
                DS4Constants.KEY_TOUCHPAD -> current.copy(buttonTouchpad = isDown)
                KeyEvent.KEYCODE_DPAD_UP -> current.copy(dpadUp = isDown)
                KeyEvent.KEYCODE_DPAD_DOWN -> current.copy(dpadDown = isDown)
                KeyEvent.KEYCODE_DPAD_LEFT -> current.copy(dpadLeft = isDown)
                KeyEvent.KEYCODE_DPAD_RIGHT -> current.copy(dpadRight = isDown)
                else -> current
            }

            val newTouched = current.touchedInputs + buttonName
            val actionStr = if (isDown) "PRESSED" else "RELEASED"
            val logEntry = InputLogEvent(
                name = buttonName,
                action = actionStr,
                value = if (isDown) "1" else "0"
            )
            val newRecent = (listOf(logEntry) + current.recentEvents).take(20)

            updated.copy(
                touchedInputs = newTouched,
                recentEvents = newRecent,
                lastUpdated = System.currentTimeMillis()
            )
        }

        _diagnosticsState.update { diag ->
            diag.copy(
                detectedInputs = diag.detectedInputs + buttonName,
                stuckButtons = stuck,
                totalEventsCount = diag.totalEventsCount + 1,
                lastEventTime = System.currentTimeMillis()
            )
        }

        return true
    }

    /**
     * Dispatched from Activity's dispatchGenericMotionEvent
     */
    fun processMotionEvent(event: MotionEvent): Boolean {
        if (!DS4Constants.isGamepadOrJoystick(event.source)) {
            return false
        }

        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)

        val l2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).takeIf { it > 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_BRAKE).coerceIn(0f, 1f)
        val r2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).takeIf { it > 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_GAS).coerceIn(0f, 1f)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val dpadL = hatX < -0.5f
        val dpadR = hatX > 0.5f
        val dpadU = hatY < -0.5f
        val dpadD = hatY > 0.5f

        // Stick centering and drift measurement
        val leftOffset = sqrt(lx * lx + ly * ly)
        val rightOffset = sqrt(rx * rx + ry * ry)

        val leftCentering = when {
            leftOffset > 0.35f -> CenteringStatus.ACTIVE_INPUT
            leftOffset > 0.18f -> CenteringStatus.SEVERE_DRIFT
            leftOffset > 0.08f -> CenteringStatus.SLIGHT_OFFSET
            else -> CenteringStatus.NEUTRAL
        }

        val rightCentering = when {
            rightOffset > 0.35f -> CenteringStatus.ACTIVE_INPUT
            rightOffset > 0.18f -> CenteringStatus.SEVERE_DRIFT
            rightOffset > 0.08f -> CenteringStatus.SLIGHT_OFFSET
            else -> CenteringStatus.NEUTRAL
        }

        val detected = mutableSetOf<String>()
        if (abs(lx) > 0.2f || abs(ly) > 0.2f) detected.add("Left Stick")
        if (abs(rx) > 0.2f || abs(ry) > 0.2f) detected.add("Right Stick")
        if (l2 > 0.1f) detected.add("L2 Trigger")
        if (r2 > 0.1f) detected.add("R2 Trigger")
        if (dpadL || dpadR || dpadU || dpadD) detected.add("D-Pad")

        _inputState.update { current ->
            var newRecent = current.recentEvents
            if (abs(lx - current.leftStickX) > 0.3f || abs(rx - current.rightStickX) > 0.3f) {
                val log = InputLogEvent(
                    name = "Analog Sticks",
                    action = "MOTION",
                    value = "L:(${String.format("%.2f", lx)}, ${String.format("%.2f", ly)}) R:(${String.format("%.2f", rx)}, ${String.format("%.2f", ry)})"
                )
                newRecent = (listOf(log) + current.recentEvents).take(20)
            }

            current.copy(
                leftStickX = lx,
                leftStickY = ly,
                rightStickX = rx,
                rightStickY = ry,
                l2Pressure = l2,
                r2Pressure = r2,
                buttonL2 = current.buttonL2 || l2 > 0.2f,
                buttonR2 = current.buttonR2 || r2 > 0.2f,
                dpadLeft = current.dpadLeft || dpadL,
                dpadRight = current.dpadRight || dpadR,
                dpadUp = current.dpadUp || dpadU,
                dpadDown = current.dpadDown || dpadD,
                touchedInputs = current.touchedInputs + detected,
                recentEvents = newRecent,
                lastUpdated = System.currentTimeMillis()
            )
        }

        _diagnosticsState.update { diag ->
            diag.copy(
                detectedInputs = diag.detectedInputs + detected,
                leftStickDriftOffset = leftOffset,
                rightStickDriftOffset = rightOffset,
                leftStickCenteringStatus = leftCentering,
                rightStickCenteringStatus = rightCentering,
                totalEventsCount = diag.totalEventsCount + 1,
                lastEventTime = System.currentTimeMillis()
            )
        }

        return true
    }

    private fun getButtonName(keyCode: Int): String? {
        return when (keyCode) {
            DS4Constants.KEY_CROSS -> "Cross (X)"
            DS4Constants.KEY_CIRCLE -> "Circle (O)"
            DS4Constants.KEY_SQUARE -> "Square (□)"
            DS4Constants.KEY_TRIANGLE -> "Triangle (△)"
            DS4Constants.KEY_L1 -> "L1 Button"
            DS4Constants.KEY_R1 -> "R1 Button"
            DS4Constants.KEY_L2 -> "L2 Trigger"
            DS4Constants.KEY_R2 -> "R2 Trigger"
            DS4Constants.KEY_L3 -> "L3 Stick Click"
            DS4Constants.KEY_R3 -> "R3 Stick Click"
            DS4Constants.KEY_SHARE -> "Share Button"
            DS4Constants.KEY_OPTIONS -> "Options Button"
            DS4Constants.KEY_PS -> "PS Button"
            DS4Constants.KEY_TOUCHPAD -> "Touchpad Click"
            KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
            else -> null
        }
    }
}
