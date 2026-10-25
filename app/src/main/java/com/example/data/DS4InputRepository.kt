package com.example.data

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.model.BatteryInfo
import com.example.model.BatteryStatus
import com.example.model.CenteringStatus
import com.example.model.ConnectionStatus
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.DiagnosticsState
import com.example.model.InputLogEvent
import com.example.model.InputState
import com.example.model.LightBarApplyResult
import com.example.model.LightBarColor
import com.example.model.MotionSensorsState
import com.example.model.TouchpadState
import com.example.model.TransportType
import com.example.protocol.DS4UsbTransmitter
import com.example.protocol.LightBarController
import com.example.sensor.MotionSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class DS4InputRepository(private val context: Context) {

    private val TAG = "DS4InputRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val controllerManager = ControllerManager(context)
    val lightBarController = LightBarController(context)
    val motionSensorManager = MotionSensorManager(context)
    private val usbTransmitter = DS4UsbTransmitter(context)

    // Connection states directly delegated to ControllerManager
    val connectionStatus: StateFlow<ConnectionStatus> = controllerManager.connectionState
    val currentDevice: StateFlow<DS4DeviceInfo?> = controllerManager.activeController
    val discoveredDevices: StateFlow<List<DS4DeviceInfo>> = controllerManager.discoveredControllers
    val isSimulationMode: StateFlow<Boolean> = controllerManager.isSimulationMode
    val batteryState: StateFlow<BatteryInfo> = controllerManager.batteryRepository.batteryState
    val motionSensors: StateFlow<MotionSensorsState> = motionSensorManager.motionSensors

    private val prefs = context.getSharedPreferences("ds4_companion_prefs", Context.MODE_PRIVATE)

    private fun loadLastLightBarColor(): LightBarColor {
        val red = prefs.getInt("lightbar_red", LightBarColor.PLAYSTATION_BLUE.red)
        val green = prefs.getInt("lightbar_green", LightBarColor.PLAYSTATION_BLUE.green)
        val blue = prefs.getInt("lightbar_blue", LightBarColor.PLAYSTATION_BLUE.blue)
        val brightness = prefs.getFloat("lightbar_brightness", LightBarColor.PLAYSTATION_BLUE.brightness)
        val flashOn = prefs.getInt("lightbar_flash_on", 0)
        val flashOff = prefs.getInt("lightbar_flash_off", 0)
        return LightBarColor(red, green, blue, brightness, flashOn, flashOff)
    }

    private fun saveLastLightBarColor(color: LightBarColor) {
        prefs.edit()
            .putInt("lightbar_red", color.red)
            .putInt("lightbar_green", color.green)
            .putInt("lightbar_blue", color.blue)
            .putFloat("lightbar_brightness", color.brightness)
            .putInt("lightbar_flash_on", color.flashOn)
            .putInt("lightbar_flash_off", color.flashOff)
            .apply()
    }

    // Light bar state (restored from persistent storage)
    private val _lightBarColor = MutableStateFlow(loadLastLightBarColor())
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
                    motionSensorManager.bindController(device.inputDeviceId)
                    // Automatically re-apply persisted light bar color upon connection/reconnection
                    val savedColor = _lightBarColor.value
                    lightBarController.applyColor(savedColor, device)
                } else {
                    motionSensorManager.unbindController()
                    resetInputTester()
                }
            }
        }

        scope.launch {
            controllerManager.isSimulationMode.collect { isSim ->
                if (isSim) {
                    motionSensorManager.startSimulation()
                    // Set initial simulated touchpad point
                    _inputState.update {
                        it.copy(
                            touchpad = TouchpadState(x = 0.5f, y = 0.5f, isTouching = false, isClicked = false)
                        )
                    }
                } else {
                    motionSensorManager.stopSimulation()
                }
            }
        }

        scope.launch {
            motionSensorManager.motionSensors.collect { sensors ->
                _inputState.update { it.copy(motionSensors = sensors) }
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
        resetInputTester()
    }

    fun setSimulationMode(enabled: Boolean) {
        controllerManager.setSimulationMode(enabled)
    }

    private fun inspectDeviceDiagnostics(device: DS4DeviceInfo) {
        val inputDev = device.inputDeviceId?.let { InputDevice.getDevice(it) }
        val ranges = mutableListOf<com.example.model.AxisRangeInfo>()
        val notes = mutableListOf<String>()

        if (inputDev != null) {
            val axesToCheck = listOf(
                MotionEvent.AXIS_X to "Left Stick X",
                MotionEvent.AXIS_Y to "Left Stick Y",
                MotionEvent.AXIS_Z to "Right Stick X",
                MotionEvent.AXIS_RZ to "Right Stick Y",
                MotionEvent.AXIS_LTRIGGER to "L2 Analog Trigger",
                MotionEvent.AXIS_RTRIGGER to "R2 Analog Trigger",
                MotionEvent.AXIS_HAT_X to "D-Pad Hat X",
                MotionEvent.AXIS_HAT_Y to "D-Pad Hat Y"
            )

            for ((axis, name) in axesToCheck) {
                val range = inputDev.getMotionRange(axis)
                if (range != null) {
                    ranges.add(
                        com.example.model.AxisRangeInfo(
                            axisName = name,
                            axisId = axis,
                            min = range.min,
                            max = range.max,
                            flat = range.flat,
                            fuzz = range.fuzz
                        )
                    )
                }
            }
            notes.add("InputDevice detected via system subsystem: ${inputDev.name}")
            if (device.isOfficialSony) {
                notes.add("Official Sony Vendor ID verified (0x054C)")
            }
        } else if (device.transport == TransportType.SIMULATION) {
            notes.add("Virtual DualShock 4 Simulation Active")
            notes.add("All hardware axes and buttons ready for interactive test")
        }

        _diagnosticsState.update { current ->
            current.copy(
                vendorIdHex = "0x" + (device.vendorId?.toString(16)?.uppercase()?.padStart(4, '0') ?: "0000"),
                productIdHex = "0x" + (device.productId?.toString(16)?.uppercase()?.padStart(4, '0') ?: "0000"),
                inputSourcesDescription = inputDev?.sources?.let { "0x" + it.toString(16) } ?: "Virtual / OTG",
                axisRanges = ranges,
                hardwareNotes = notes
            )
        }
    }

    fun applyLightBarColor(color: LightBarColor) {
        _lightBarColor.value = color
        saveLastLightBarColor(color)
        val device = currentDevice.value
        val result = lightBarController.applyColor(color, device)
        _lightBarResult.value = result
    }

    fun resetLightBarColor() {
        applyLightBarColor(LightBarColor.PLAYSTATION_BLUE)
    }

    fun resetInputTester() {
        val isHardwareActive = controllerManager.activeController.value != null || controllerManager.isSimulationMode.value
        _inputState.update {
            InputState(
                touchedInputs = emptySet(),
                recentEvents = emptyList(),
                touchpad = TouchpadState(
                    x = 0.5f,
                    y = 0.5f,
                    isTouching = false,
                    isClicked = false,
                    touchCount = 0,
                    isHardwareSupported = isHardwareActive,
                    x2 = 0f,
                    y2 = 0f,
                    isTouching2 = false,
                    id = 0
                )
            )
        }
    }

    /**
     * Programmatic simulation/injection for touchpad contact points.
     * Only permitted when Simulation Mode is explicitly enabled.
     */
    fun simulateTouchpad(
        x: Float,
        y: Float,
        isTouching: Boolean,
        isClicked: Boolean,
        x2: Float = 0f,
        y2: Float = 0f,
        isTouching2: Boolean = false
    ) {
        if (!controllerManager.isSimulationMode.value) {
            return
        }
        val count = when {
            isTouching && isTouching2 -> 2
            isTouching -> 1
            else -> 0
        }
        val detected = mutableSetOf<String>()
        if (isTouching) detected.add("Touchpad Surface")
        if (isClicked) detected.add("Touchpad Click")

        _inputState.update { current ->
            var newRecent = current.recentEvents
            if (isClicked && !current.buttonTouchpad) {
                val log = InputLogEvent(name = "Touchpad Click", action = "PRESSED", value = "1")
                newRecent = (listOf(log) + current.recentEvents).take(20)
            } else if (!isClicked && current.buttonTouchpad) {
                val log = InputLogEvent(name = "Touchpad Click", action = "RELEASED", value = "0")
                newRecent = (listOf(log) + current.recentEvents).take(20)
            }

            current.copy(
                buttonTouchpad = isClicked,
                touchpad = current.touchpad.copy(
                    x = x.coerceIn(0f, 1f),
                    y = y.coerceIn(0f, 1f),
                    isTouching = isTouching,
                    isClicked = isClicked,
                    touchCount = count,
                    x2 = x2.coerceIn(0f, 1f),
                    y2 = y2.coerceIn(0f, 1f),
                    isTouching2 = isTouching2
                ),
                touchedInputs = current.touchedInputs + detected,
                recentEvents = newRecent,
                lastUpdated = System.currentTimeMillis()
            )
        }

        if (isTouching || isClicked) {
            _diagnosticsState.update { diag ->
                diag.copy(
                    detectedInputs = diag.detectedInputs + detected,
                    totalEventsCount = diag.totalEventsCount + 1,
                    lastEventTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun clearDiagnostics() {
        buttonPressStartTimes.clear()
        _diagnosticsState.update { current ->
            current.copy(
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
        val isTouchpad = DS4Constants.isTouchpadKey(keyCode)
        val buttonName = if (isTouchpad) "Touchpad Click" else (getButtonName(keyCode) ?: return false)

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
            val updated = when {
                isTouchpad -> current.copy(
                    buttonTouchpad = isDown,
                    touchpad = current.touchpad.copy(isClicked = isDown)
                )
                keyCode == DS4Constants.KEY_CROSS -> current.copy(buttonCross = isDown)
                keyCode == DS4Constants.KEY_CIRCLE -> current.copy(buttonCircle = isDown)
                keyCode == DS4Constants.KEY_SQUARE -> current.copy(buttonSquare = isDown)
                keyCode == DS4Constants.KEY_TRIANGLE -> current.copy(buttonTriangle = isDown)
                keyCode == DS4Constants.KEY_L1 -> current.copy(buttonL1 = isDown)
                keyCode == DS4Constants.KEY_R1 -> current.copy(buttonR1 = isDown)
                keyCode == DS4Constants.KEY_L2 -> current.copy(
                    buttonL2 = isDown,
                    l2Pressure = if (isDown && current.l2Pressure == 0f) 1f else current.l2Pressure
                )
                keyCode == DS4Constants.KEY_R2 -> current.copy(
                    buttonR2 = isDown,
                    r2Pressure = if (isDown && current.r2Pressure == 0f) 1f else current.r2Pressure
                )
                keyCode == DS4Constants.KEY_L3 -> current.copy(buttonL3 = isDown)
                keyCode == DS4Constants.KEY_R3 -> current.copy(buttonR3 = isDown)
                keyCode == DS4Constants.KEY_SHARE -> current.copy(buttonShare = isDown)
                keyCode == DS4Constants.KEY_OPTIONS -> current.copy(buttonOptions = isDown)
                keyCode == DS4Constants.KEY_PS -> current.copy(buttonPs = isDown)
                keyCode == KeyEvent.KEYCODE_DPAD_UP -> current.copy(dpadUp = isDown)
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> current.copy(dpadDown = isDown)
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> current.copy(dpadLeft = isDown)
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> current.copy(dpadRight = isDown)
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
     * Dispatched from Activity's dispatchTouchEvent for external touchpad/pointer events.
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        return handlePointerOrTouchEvent(event)
    }

    /**
     * Dispatched from Activity's dispatchGenericMotionEvent.
     */
    fun processMotionEvent(event: MotionEvent): Boolean {
        var handled = false
        if (isTouchpadOrPointerEvent(event)) {
            handled = handlePointerOrTouchEvent(event)
        }

        if (DS4Constants.isGamepadOrJoystick(event.source)) {
            handled = handleGamepadMotionEvent(event) || handled
        }
        return handled
    }

    /**
     * Identifies if a MotionEvent originates genuinely from a DualShock 4 controller's
     * physical touchpad/pointer sub-device and NEVER from the phone's touchscreen or standard external mice.
     */
    fun isDs4PointerEvent(event: MotionEvent): Boolean {
        // 1. Phone touchscreen touches must NEVER trigger controller touchpad input
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return false
        }
        val dev = event.device ?: return false
        if ((dev.sources and InputDevice.SOURCE_TOUCHSCREEN) != 0) {
            return false
        }
        if (dev.isVirtual && !controllerManager.isSimulationMode.value) {
            return false
        }

        // 2. Must be pointer, mouse, or touchpad input source
        val src = event.source
        val isPointerSource = (src and InputDevice.SOURCE_TOUCHPAD != 0) ||
                (src and InputDevice.SOURCE_MOUSE != 0) ||
                (src and InputDevice.SOURCE_TRACKBALL != 0) ||
                (src and InputDevice.SOURCE_CLASS_POINTER != 0)
        if (!isPointerSource) {
            return false
        }

        // If simulation mode is active, allow virtual pointer events
        if (controllerManager.isSimulationMode.value) {
            return true
        }

        // Must have an active controller connected
        val activeDev = currentDevice.value ?: return false

        // 3. Match device ID or genuine DualShock 4 hardware signature (never match generic touchpads)
        val isMatchingActiveDevice = activeDev.inputDeviceId != null && dev.id == activeDev.inputDeviceId
        val devName = (dev.name ?: "").lowercase()
        val isSonyDs4 = DS4Constants.isDualShock4(dev.vendorId, dev.productId, dev.name)
        val isDs4Name = devName.contains("wireless controller") || devName.contains("dualshock")

        return isMatchingActiveDevice || isSonyDs4 || isDs4Name
    }

    private fun isTouchpadOrPointerEvent(event: MotionEvent): Boolean {
        return isDs4PointerEvent(event)
    }

    private fun handlePointerOrTouchEvent(event: MotionEvent): Boolean {
        if (!isDs4PointerEvent(event)) {
            return false
        }

        val dev = event.device ?: return false
        val src = event.source
        val action = event.actionMasked
        val isReleased = action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_HOVER_EXIT ||
                action == MotionEvent.ACTION_CANCEL

        val isTouching = !isReleased && (
                action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_MOVE ||
                action == MotionEvent.ACTION_HOVER_MOVE ||
                action == MotionEvent.ACTION_SCROLL
        )

        val isPrimaryClick = (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) ||
                (action == MotionEvent.ACTION_BUTTON_PRESS && event.actionButton == MotionEvent.BUTTON_PRIMARY)

        _inputState.update { current ->
            val detected = mutableSetOf<String>()
            if (isTouching) {
                detected.add("Touchpad Surface")
            }
            if (isPrimaryClick) {
                detected.add("Touchpad Click")
            }

            var newRecent = current.recentEvents
            if (isPrimaryClick && !current.buttonTouchpad) {
                val log = InputLogEvent(
                    name = "Touchpad Click",
                    action = "PRESSED",
                    value = "1"
                )
                newRecent = (listOf(log) + current.recentEvents).take(20)
            } else if (!isPrimaryClick && current.buttonTouchpad && (action == MotionEvent.ACTION_BUTTON_RELEASE || isReleased)) {
                val log = InputLogEvent(
                    name = "Touchpad Click",
                    action = "RELEASED",
                    value = "0"
                )
                newRecent = (listOf(log) + current.recentEvents).take(20)
            }

            // Read normalized coordinates (0.0 to 1.0)
            var normX = current.touchpad.x
            var normY = current.touchpad.y

            if (isTouching) {
                if (src and InputDevice.SOURCE_TOUCHPAD != 0) {
                    val xRange = dev.getMotionRange(MotionEvent.AXIS_X)?.range ?: 1920f
                    val yRange = dev.getMotionRange(MotionEvent.AXIS_Y)?.range ?: 942f
                    normX = (event.x / (if (xRange > 1f) xRange else 1920f)).coerceIn(0f, 1f)
                    normY = (event.y / (if (yRange > 1f) yRange else 942f)).coerceIn(0f, 1f)
                } else {
                    // Relative mouse motion from DS4 touchpad
                    val relX = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    } else 0f
                    val relY = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    } else 0f

                    if (relX != 0f || relY != 0f) {
                        normX = (current.touchpad.x + (relX / 800f)).coerceIn(0.02f, 0.98f)
                        normY = (current.touchpad.y + (relY / 400f)).coerceIn(0.02f, 0.98f)
                    }
                    // Never fall back to screen dimensions.
                }
            }

            // Multi-touch support for DualShock 4 (supports 2 concurrent contact points)
            val hasSecondPointer = isTouching && event.pointerCount >= 2
            var normX2 = current.touchpad.x2
            var normY2 = current.touchpad.y2

            if (hasSecondPointer) {
                if (src and InputDevice.SOURCE_TOUCHPAD != 0) {
                    val xRange = dev.getMotionRange(MotionEvent.AXIS_X)?.range ?: 1920f
                    val yRange = dev.getMotionRange(MotionEvent.AXIS_Y)?.range ?: 942f
                    normX2 = (event.getX(1) / (if (xRange > 1f) xRange else 1920f)).coerceIn(0f, 1f)
                    normY2 = (event.getY(1) / (if (yRange > 1f) yRange else 942f)).coerceIn(0f, 1f)
                }
            } else if (!isTouching) {
                normX2 = 0f
                normY2 = 0f
            }

            val contactId = if (isTouching && event.pointerCount > 0) event.getPointerId(0) else 0

            current.copy(
                buttonTouchpad = isPrimaryClick,
                touchpad = current.touchpad.copy(
                    x = normX,
                    y = normY,
                    isTouching = isTouching,
                    isClicked = isPrimaryClick,
                    touchCount = if (isTouching) maxOf(event.pointerCount, 1) else 0,
                    isHardwareSupported = true,
                    x2 = normX2,
                    y2 = normY2,
                    isTouching2 = hasSecondPointer,
                    id = contactId
                ),
                touchedInputs = current.touchedInputs + detected,
                recentEvents = newRecent,
                lastUpdated = System.currentTimeMillis()
            )
        }

        if (isTouching || isPrimaryClick) {
            _diagnosticsState.update { diag ->
                diag.copy(
                    detectedInputs = diag.detectedInputs + (if (isTouching) setOf("Touchpad Surface") else emptySet()) + (if (isPrimaryClick) setOf("Touchpad Click") else emptySet()),
                    totalEventsCount = diag.totalEventsCount + 1,
                    lastEventTime = System.currentTimeMillis()
                )
            }
        }

        return true
    }

    private fun handleGamepadMotionEvent(event: MotionEvent): Boolean {
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
                buttonL2 = l2 > 0.2f,
                buttonR2 = r2 > 0.2f,
                dpadLeft = dpadL,
                dpadRight = dpadR,
                dpadUp = dpadU,
                dpadDown = dpadD,
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
        if (DS4Constants.isTouchpadKey(keyCode)) {
            return "Touchpad Click"
        }
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
            KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
            else -> null
        }
    }
}
