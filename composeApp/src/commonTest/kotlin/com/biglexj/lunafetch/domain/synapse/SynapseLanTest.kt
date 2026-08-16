package com.biglexj.lunafetch.domain.synapse

import com.biglexj.lunafetch.domain.DownloadHistoryItem
import com.biglexj.lunafetch.domain.synapse.lan.LanBeaconPacket
import com.biglexj.lunafetch.domain.synapse.lan.LanHistorySyncRequest
import com.biglexj.lunafetch.domain.synapse.lan.PushDownloadRequest
import com.biglexj.lunafetch.domain.synapse.lan.SynapseDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SynapseLanTest {

    @Test
    fun testBeaconPacketSerialization() {
        val beacon = LanBeaconPacket(
            deviceId = "luna-desktop-01",
            deviceName = "Biglex-PC",
            deviceType = "desktop",
            port = 49288,
            os = "windows",
        )
        val json = LanBeaconPacket.toJson(beacon)
        val parsed = LanBeaconPacket.fromJson(json)

        assertNotNull(parsed)
        assertEquals("luna-desktop-01", parsed.deviceId)
        assertEquals("Biglex-PC", parsed.deviceName)
        assertEquals(49288, parsed.port)
        assertEquals("windows", parsed.os)
    }

    @Test
    fun testPushDownloadRequestSerialization() {
        val req = PushDownloadRequest(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            mediaType = "audio",
            quality = "320",
            sourceDevice = "Galaxy S24",
            autoPlay = true,
        )
        val json = PushDownloadRequest.toJson(req)
        val parsed = PushDownloadRequest.fromJson(json)

        assertNotNull(parsed)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsed.url)
        assertEquals("audio", parsed.mediaType)
        assertEquals("Galaxy S24", parsed.sourceDevice)
        assertTrue(parsed.autoPlay)
    }

    @Test
    fun testHistorySyncRequestSerialization() {
        val item = DownloadHistoryItem(
            id = "item_123",
            title = "Test Song",
            formatLabel = "MP3",
            path = "D:/Music/test.mp3",
            url = "https://youtu.be/123",
            timestampMs = 1700000000000L,
        )
        val req = LanHistorySyncRequest(
            sourceDevice = "Galaxy S24",
            historyItems = listOf(item),
        )
        val json = LanHistorySyncRequest.toJson(req)
        val parsed = LanHistorySyncRequest.fromJson(json)

        assertNotNull(parsed)
        assertEquals(1, parsed.historyItems.size)
        assertEquals("Test Song", parsed.historyItems[0].title)
    }

    @Test
    fun testSynapseDeviceOnlineStatus() {
        val device = SynapseDevice(
            id = "dev_1",
            name = "Test Laptop",
            type = "laptop",
            ip = "192.168.1.50",
            port = 49288,
            os = "linux",
            lastSeenMs = System.currentTimeMillis(),
        )
        assertTrue(device.isOnline)
        assertEquals("💻", device.icon)
    }
}
