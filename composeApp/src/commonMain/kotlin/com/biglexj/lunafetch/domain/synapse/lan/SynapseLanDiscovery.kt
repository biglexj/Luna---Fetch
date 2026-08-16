package com.biglexj.lunafetch.domain.synapse.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

/**
 * Motor de Descubrimiento Zero-Config en Red Local (UDP Broadcast Beacon).
 * Puerto reservado: 49289 (UDP).
 *
 * Permite que dispositivos móviles (Android) y de escritorio (Windows/Linux)
 * se detecten mutuamente en la misma red Wi-Fi sin configurar direcciones IP.
 */
class SynapseLanDiscovery(
    val localDevice: SynapseDevice,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    companion object {
        const val BEACON_PORT = 49289
        const val BEACON_INTERVAL_MS = 4000L
        const val DEVICE_TIMEOUT_MS = 14000L
    }

    private val _discoveredDevices = MutableStateFlow<List<SynapseDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<SynapseDevice>> = _discoveredDevices.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenerSocket: DatagramSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        startListening()
        startBroadcasting()
        startPruningStaleDevices()
    }

    fun stop() {
        isRunning = false
        broadcastJob?.cancel()
        runCatching { listenerSocket?.close() }
        listenerSocket = null
        _discoveredDevices.value = emptyList()
    }

    private fun startListening() {
        thread(isDaemon = true, name = "LunaSynapseLanDiscoveryListener") {
            try {
                val socket = DatagramSocket(BEACON_PORT, InetAddress.getByName("0.0.0.0")).apply {
                    broadcast = true
                    reuseAddress = true
                }
                listenerSocket = socket
                val buffer = ByteArray(2048)

                while (isRunning && !socket.isClosed) {
                    runCatching {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val senderIp = packet.address.hostAddress
                        val json = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val beacon = LanBeaconPacket.fromJson(json)

                        if (beacon != null && beacon.deviceId != localDevice.id && !senderIp.isNullOrBlank()) {
                            val device = SynapseDevice(
                                id = beacon.deviceId,
                                name = beacon.deviceName,
                                type = beacon.deviceType,
                                ip = senderIp,
                                port = beacon.port,
                                os = beacon.os,
                                lastSeenMs = System.currentTimeMillis(),
                            )
                            updateDevice(device)
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket cerrado o puerto en uso
            }
        }
    }

    private fun startBroadcasting() {
        broadcastJob = scope.launch {
            val beacon = LanBeaconPacket(
                deviceId = localDevice.id,
                deviceName = localDevice.name,
                deviceType = localDevice.type,
                port = localDevice.port,
                os = localDevice.os,
            )
            val json = LanBeaconPacket.toJson(beacon)
            val bytes = json.toByteArray(Charsets.UTF_8)

            while (isActive && isRunning) {
                broadcastPacket(bytes)
                delay(BEACON_INTERVAL_MS)
            }
        }
    }

    private fun broadcastPacket(bytes: ByteArray) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true

                // Broadcast general
                val generalBroadcast = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(bytes, bytes.size, generalBroadcast, BEACON_PORT))

                // Broadcast por interfaces de red activas
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        val broadcast = interfaceAddress.broadcast ?: continue
                        runCatching {
                            socket.send(DatagramPacket(bytes, bytes.size, broadcast, BEACON_PORT))
                        }
                    }
                }
            }
        }
    }

    private fun updateDevice(device: SynapseDevice) {
        _discoveredDevices.update { list ->
            val now = System.currentTimeMillis()
            val existing = list.filter { it.id != device.id && (now - it.lastSeenMs) < DEVICE_TIMEOUT_MS }
            existing + device
        }
    }

    private fun startPruningStaleDevices() {
        scope.launch {
            while (isActive && isRunning) {
                delay(5000L)
                val now = System.currentTimeMillis()
                _discoveredDevices.update { list ->
                    list.filter { (now - it.lastSeenMs) < DEVICE_TIMEOUT_MS }
                }
            }
        }
    }
}
