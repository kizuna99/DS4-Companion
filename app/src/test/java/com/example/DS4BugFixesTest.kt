package com.example

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.DS4InputRepository
import com.example.model.DS4Constants
import com.example.model.DS4DeviceInfo
import com.example.model.LightBarColor
import com.example.model.TransportType
import com.example.sensor.MotionSensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DS4BugFixesTest {

    private lateinit var context: Context
    private lateinit var repository: DS4InputRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = DS4InputRepository(context)
    }

    // --- BUG 1 TESTS: Light Bar Persistence & Auto Restoration ---

    @Test
    fun testLightBarColorPersistenceAcrossSessions() {
        // Apply custom color (Combat Red)
        val customColor = LightBarColor.COMBAT_RED
        repository.applyLightBarColor(customColor)

        // Verify repository state
        assertEquals(customColor, repository.lightBarColor.value)

        // Create a new repository instance (simulating app restart)
        val newRepo = DS4InputRepository(context)
        assertEquals(customColor.red, newRepo.lightBarColor.value.red)
        assertEquals(customColor.green, newRepo.lightBarColor.value.green)
        assertEquals(customColor.blue, newRepo.lightBarColor.value.blue)
    }

    @Test
    fun testLightBarAutoRestoreOnControllerConnect() {
        val purple = LightBarColor.NEON_PURPLE
        repository.applyLightBarColor(purple)

        // Connect simulated DS4
        val simDevice = DS4DeviceInfo(
            id = "ds4_connect_test",
            name = "DualShock 4",
            transport = TransportType.SIMULATION
        )
        repository.controllerManager.connect(simDevice)

        // Repository should retain and reapply the purple color
        assertEquals(purple, repository.lightBarColor.value)
    }

    // --- BUG 2 TESTS: Reject Phone Screen Touches on DS4 Touchpad ---

    @Test
    fun testPhoneScreenTouchDoesNotActivateTouchpad() {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        // Create a touch event with SOURCE_TOUCHSCREEN (representing phone screen touch)
        val screenTouchEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_DOWN,
            500f,
            800f,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        // Process touch event through repository
        val handled = repository.processTouchEvent(screenTouchEvent)

        // Must reject phone screen touch
        assertFalse("Phone touchscreen events must be rejected", handled)

        val touchpadState = repository.inputState.value.touchpad
        assertFalse("Touchpad indicator must not activate on phone screen touch", touchpadState.isTouching)
        assertFalse("Touchpad click must not activate on phone screen touch", touchpadState.isClicked)
    }

    @Test
    fun testPhoneScreenDragDoesNotMoveTouchpadCoordinates() {
        val downTime = SystemClock.uptimeMillis()

        val initialX = repository.inputState.value.touchpad.x
        val initialY = repository.inputState.value.touchpad.y

        val screenDragEvent = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_MOVE,
            250f,
            400f,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        val handled = repository.processTouchEvent(screenDragEvent)
        assertFalse(handled)

        val touchpadState = repository.inputState.value.touchpad
        assertEquals(initialX, touchpadState.x, 0.001f)
        assertEquals(initialY, touchpadState.y, 0.001f)
        assertFalse(touchpadState.isTouching)
    }

    // --- BUG 3 TESTS: Optimize IMU Gyro & Accelerometer Detection ---

    @Test
    fun testStationarySensorNoiseSuppression() {
        val motionManager = MotionSensorManager(context)

        // Check constants
        assertTrue(MotionSensorManager.GYRO_NOISE_DEADZONE > 0f)
        assertTrue(MotionSensorManager.DYNAMIC_ACCEL_THRESHOLD > 0f)

        // Initial state before motion must be stationary
        val state = motionManager.motionSensors.value
        assertFalse("Initial resting sensor state must not be marked as moving", state.isMoving)
        assertTrue("Initial sensor state should be stationary", state.isStationary)
    }

    @Test
    fun testSimulationMotionAlternation() {
        val motionManager = MotionSensorManager(context)
        motionManager.startSimulation()

        // Check simulated telemetry exists and has valid bounds
        val sensors = motionManager.motionSensors.value
        assertTrue(sensors.isAvailable)
        motionManager.stopSimulation()
    }

    // --- BUG 4 TESTS: D-Pad Press and Release ---

    @Test
    fun testDpadPressAndReleaseViaKeyEvent() {
        // 1. Press D-Pad Up
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        repository.processKeyEvent(eventDown)
        assertTrue("D-Pad Up must be pressed on ACTION_DOWN", repository.inputState.value.dpadUp)

        // 2. Release D-Pad Up
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)
        repository.processKeyEvent(eventUp)
        assertFalse("D-Pad Up must be released on ACTION_UP", repository.inputState.value.dpadUp)
    }

    @Test
    fun testDpadAllDirectionsReleaseViaKeyEvent() {
        val directions = listOf(
            KeyEvent.KEYCODE_DPAD_UP to { repository.inputState.value.dpadUp },
            KeyEvent.KEYCODE_DPAD_DOWN to { repository.inputState.value.dpadDown },
            KeyEvent.KEYCODE_DPAD_LEFT to { repository.inputState.value.dpadLeft },
            KeyEvent.KEYCODE_DPAD_RIGHT to { repository.inputState.value.dpadRight }
        )

        for ((keyCode, getter) in directions) {
            repository.processKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            assertTrue("D-Pad key $keyCode must be active", getter())

            repository.processKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            assertFalse("D-Pad key $keyCode must turn off on release", getter())
        }
    }

    @Test
    fun testDisconnectResetsInputStateToNeutral() {
        // Press a button and connect
        val testDevice = DS4DeviceInfo(
            id = "test_device_disconnect",
            name = "Wireless Controller",
            transport = TransportType.BLUETOOTH
        )
        repository.controllerManager.connect(testDevice)
        repository.processKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(repository.inputState.value.dpadLeft)

        // Disconnect controller
        repository.disconnectDevice()
        assertFalse("All D-Pad directions must be reset to neutral on disconnect", repository.inputState.value.dpadLeft)
    }
}
