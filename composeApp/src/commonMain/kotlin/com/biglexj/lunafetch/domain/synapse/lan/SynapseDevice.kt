package com.biglexj.lunafetch.domain.synapse.lan

import kotlinx.serialization.Serializable

/**
 * Representa un dispositivo par descubierto en la red local (Wi-Fi / LAN)
 * bajo el protocolo Aurora Synapse LAN Link.
 */
@Serializable
data class SynapseDevice(
    val id: String,
    val name: String,
    val type: String, // "desktop" | "mobile" | "laptop"
    val ip: String,
    val port: Int = 49288,
    val os: String, // "windows" | "android" | "linux" | "macos"
    val lastSeenMs: Long = System.currentTimeMillis(),
) {
    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastSeenMs) < 15_000

    val icon: String
        get() = when (type.lowercase()) {
            "mobile", "android", "ios", "phone" -> "📱"
            "laptop" -> "💻"
            else -> "🖥️"
        }
}
