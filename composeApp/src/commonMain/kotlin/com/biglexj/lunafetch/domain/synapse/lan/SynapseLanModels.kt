package com.biglexj.lunafetch.domain.synapse.lan

import com.biglexj.lunafetch.domain.DownloadHistoryItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LanBeaconPacket(
    @SerialName("synapse_version")
    val synapseVersion: String = "1.0",

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("device_name")
    val deviceName: String,

    @SerialName("device_type")
    val deviceType: String,

    @SerialName("port")
    val port: Int = 49288,

    @SerialName("os")
    val os: String,

    @SerialName("target_app")
    val targetApp: String = "luna",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(raw: String): LanBeaconPacket? = runCatching {
            json.decodeFromString<LanBeaconPacket>(raw)
        }.getOrNull()

        fun toJson(packet: LanBeaconPacket): String = json.encodeToString(serializer(), packet)
    }
}

@Serializable
data class PushDownloadRequest(
    @SerialName("url")
    val url: String,

    @SerialName("media_type")
    val mediaType: String = "video",

    @SerialName("quality")
    val quality: String? = null,

    @SerialName("source_device")
    val sourceDevice: String,

    @SerialName("auto_play")
    val autoPlay: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(raw: String): PushDownloadRequest? = runCatching {
            json.decodeFromString<PushDownloadRequest>(raw)
        }.getOrNull()

        fun toJson(req: PushDownloadRequest): String = json.encodeToString(serializer(), req)
    }
}

@Serializable
data class LanHistorySyncRequest(
    @SerialName("source_device")
    val sourceDevice: String,

    @SerialName("history_items")
    val historyItems: List<DownloadHistoryItem> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(raw: String): LanHistorySyncRequest? = runCatching {
            json.decodeFromString<LanHistorySyncRequest>(raw)
        }.getOrNull()

        fun toJson(req: LanHistorySyncRequest): String = json.encodeToString(serializer(), req)
    }
}

@Serializable
data class LanGenericResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("message")
    val message: String,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun toJson(res: LanGenericResponse): String = json.encodeToString(serializer(), res)
    }
}

/**
 * Anuncio de nueva release propagado por Aurora Synapse LAN.
 * Regla `auto_updater.md` #11 — release messages MUST be propagated to Aurora.
 */
@Serializable
data class LanAnnounceReleaseRequest(
    @SerialName("source_device")
    val sourceDevice: String,

    @SerialName("version")
    val version: String,

    @SerialName("download_url")
    val downloadUrl: String = "",

    @SerialName("release_page_url")
    val releasePageUrl: String = "",

    @SerialName("body")
    val body: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun fromJson(raw: String): LanAnnounceReleaseRequest? = runCatching {
            json.decodeFromString<LanAnnounceReleaseRequest>(raw)
        }.getOrNull()

        fun toJson(req: LanAnnounceReleaseRequest): String = json.encodeToString(serializer(), req)
    }
}
