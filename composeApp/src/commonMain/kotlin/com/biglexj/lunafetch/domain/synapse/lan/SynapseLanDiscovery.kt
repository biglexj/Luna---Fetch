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
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), BEACON_PORT))
                }
                listenerSocket = socket
                val buffer = ByteArray(2048)

                while (isRunning && !socket.isClosed) {
                    runCatching {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val senderIp = packet.address?.hostAddress.orEmpty().substringBefore("%")
                        val json = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val beacon = LanBeaconPacket.fromJson(json)

                        if (beacon != null && senderIp.isNotBlank() && !isSelf(beacon, senderIp)) {
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

                            // Handshake HTTP de retorno en corrutina segura (pool de IO)
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    val url = java.net.URL("http://${device.ip}:${device.port}/api/v1/synapse/ping?source=${java.net.URLEncoder.encode(localDevice.name, "UTF-8")}&os=${localDevice.os}")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    conn.connectTimeout = 2000
                                    conn.readTimeout = 2000
                                    conn.requestMethod = "GET"
                                    conn.responseCode
                                    conn.disconnect()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket cerrado o puerto en uso
            }
        }
    }

    fun registerDirectPeer(device: SynapseDevice) {
        if (device.name.equals(localDevice.name, ignoreCase = true) || isLocalHostAddress(device.ip)) return
        updateDevice(device)
    }

    private fun isLocalHostAddress(ip: String): Boolean {
        if (ip == "127.0.0.1" || ip == "localhost" || ip == "::1") return true
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val host = addr.hostAddress?.substringBefore("%")
                    if (host == ip) return true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun isSelf(beacon: LanBeaconPacket, senderIp: String): Boolean {
        if (beacon.deviceId == localDevice.id) return true
        if (beacon.deviceName.equals(localDevice.name, ignoreCase = true)) return true
        if (isLocalHostAddress(senderIp)) return true
        return false
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

            // Ráfaga inicial para descubrimiento instantáneo al abrir la app o QuickDownload
            repeat(3) {
                if (isActive && isRunning) {
                    broadcastPacket(bytes)
                    delay(350L)
                }
            }

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
            val existing = list.filter {
                it.id != device.id &&
                !it.name.equals(device.name, ignoreCase = true) &&
                it.ip != device.ip &&
                (now - it.lastSeenMs) < DEVICE_TIMEOUT_MS
            }
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
