package com.dualstream.network

import android.content.Context
import android.os.Build
import com.dualstream.model.ConnectionState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyConnectionManagerTest {

    private lateinit var classUnderTest: NearbyConnectionManager
    private lateinit var context: Context
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var streamReceiver: StreamReceiver

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        Dispatchers.setMain(StandardTestDispatcher())
        context = mockk(relaxed = true)
        connectionsClient = mockk(relaxed = true)
        streamReceiver = mockk(relaxed = true)

        mockkStatic(Nearby::class)
        every { Nearby.getConnectionsClient(any<Context>()) } returns connectionsClient
        
        mockkStatic(GoogleApiAvailability::class)
        val apiAvail = mockk<GoogleApiAvailability>(relaxed = true)
        every { GoogleApiAvailability.getInstance() } returns apiAvail
        every { apiAvail.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS

        // Mock Task returns
        every { connectionsClient.startDiscovery(any(), any(), any()) } returns mockk(relaxed = true)
        every { connectionsClient.requestConnection(any<String>(), any<String>(), any()) } returns mockk(relaxed = true)
        every { connectionsClient.acceptConnection(any<String>(), any()) } returns mockk(relaxed = true)
        every { connectionsClient.sendPayload(any<String>(), any()) } returns mockk(relaxed = true)
        
        mockkConstructor(org.json.JSONObject::class)
        every { anyConstructed<org.json.JSONObject>().toString() } returns "{}"

        classUnderTest = NearbyConnectionManager(context, streamReceiver)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun getCallback(): ConnectionLifecycleCallback {
        val field: Field = NearbyConnectionManager::class.java.getDeclaredField("connectionLifecycleCallback")
        field.isAccessible = true
        return field.get(classUnderTest) as ConnectionLifecycleCallback
    }

    @Test
    fun testConnectionState_CarriesHumanReadableName() {
        val callback = getCallback()
        val endpointId = "ABC"
        val endpointName = "Kesav's Pixel 7"
        
        val connectionInfo = mockk<ConnectionInfo>(relaxed = true)
        every { connectionInfo.endpointName } returns endpointName

        // Simulate Connection Initiated
        callback.onConnectionInitiated(endpointId, connectionInfo)

        var state = classUnderTest.connectionState.value
        assertTrue(state is ConnectionState.Connecting)
        assertEquals(endpointName, (state as ConnectionState.Connecting).deviceName)

        // Simulate Connection Success
        val resolution = mockk<ConnectionResolution>(relaxed = true)
        every { resolution.status.isSuccess } returns true
        callback.onConnectionResult(endpointId, resolution)

        state = classUnderTest.connectionState.value
        assertTrue(state is ConnectionState.Connected)
        assertEquals(endpointName, (state as ConnectionState.Connected).deviceName)
    }

    @Test
    fun testReconnection_RetriesAndEventuallyFails() = runTest {
        val callback = getCallback()
        val endpointId = "ABC"
        val endpointName = "Phone B"
        
        val connectionInfo = mockk<ConnectionInfo>(relaxed = true)
        every { connectionInfo.endpointName } returns endpointName

        // Connect
        callback.onConnectionInitiated(endpointId, connectionInfo)
        val resolution = mockk<ConnectionResolution>(relaxed = true)
        every { resolution.status.isSuccess } returns true
        callback.onConnectionResult(endpointId, resolution)
        
        // Start as Sender to test discovery reconnection
        val modeField = NearbyConnectionManager::class.java.getDeclaredField("currentMode")
        modeField.isAccessible = true
        modeField.set(classUnderTest, com.dualstream.model.AppMode.SENDER)

        // Disconnect 1st time
        callback.onDisconnected(endpointId)
        
        // Advance time to allow first reconnection delay (1000ms)
        testScheduler.advanceTimeBy(1100)
        verify(exactly = 1) { connectionsClient.startDiscovery(any(), any(), any()) }

        // It failed to connect, disconnect again (2nd)
        resetConnectionStateToDisconnected()
        callback.onDisconnected(endpointId)
        testScheduler.advanceTimeBy(2100)
        verify(exactly = 2) { connectionsClient.startDiscovery(any(), any(), any()) }

        // Disconnect 3rd
        resetConnectionStateToDisconnected()
        callback.onDisconnected(endpointId)
        testScheduler.advanceTimeBy(3100)
        verify(exactly = 3) { connectionsClient.startDiscovery(any(), any(), any()) }

        // Disconnect 4th
        resetConnectionStateToDisconnected()
        callback.onDisconnected(endpointId)
        testScheduler.advanceTimeBy(4100)
        verify(exactly = 4) { connectionsClient.startDiscovery(any(), any(), any()) }

        // Disconnect 5th
        resetConnectionStateToDisconnected()
        callback.onDisconnected(endpointId)
        testScheduler.advanceTimeBy(5100)
        verify(exactly = 5) { connectionsClient.startDiscovery(any(), any(), any()) }

        // Disconnect 6th (Exceeds maxReconnectAttempts = 5)
        resetConnectionStateToDisconnected()
        callback.onDisconnected(endpointId)
        testScheduler.advanceTimeBy(10000)
        
        // StartDiscovery should NOT be called again
        verify(exactly = 5) { connectionsClient.startDiscovery(any(), any(), any()) }
    }
    
    private fun resetConnectionStateToDisconnected() {
        val field: Field = NearbyConnectionManager::class.java.getDeclaredField("_connectionState")
        field.isAccessible = true
        val stateFlow = field.get(classUnderTest) as kotlinx.coroutines.flow.MutableStateFlow<ConnectionState>
        stateFlow.value = ConnectionState.Disconnected
    }
}
