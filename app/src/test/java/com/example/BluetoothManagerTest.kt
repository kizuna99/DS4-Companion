package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bluetooth.BluetoothManager
import com.example.model.ConnectionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothManagerTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        bluetoothManager = BluetoothManager(context)
    }

    @After
    fun tearDown() {
        bluetoothManager.release()
    }

    @Test
    fun testInitialConnectionState() {
        val state = bluetoothManager.connectionState.value
        assertEquals(ConnectionStatus.DISCONNECTED, state)
        assertNull(bluetoothManager.connectedDevice.value)
        assertFalse(bluetoothManager.isScanning.value)
    }

    @Test
    fun testDisconnectResetsState() {
        bluetoothManager.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, bluetoothManager.connectionState.value)
        assertNull(bluetoothManager.connectedDevice.value)
    }

    @Test
    fun testDiscoveredDevicesInitiallyNotNull() {
        assertNotNull(bluetoothManager.discoveredDevices.value)
    }
}
