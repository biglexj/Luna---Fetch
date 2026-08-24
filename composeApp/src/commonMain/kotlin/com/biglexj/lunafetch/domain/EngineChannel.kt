package com.biglexj.lunafetch.domain

/**
 * Canal de distribución del motor (yt-dlp) usado en el Centro de Actualizaciones.
 *
 * - [wire]: valor persistido (Backend / SharedPreferences).
 * - [ytdlpValue]: token admitido por `yt-dlp --update-to` y por
 *   `YoutubeDL.UpdateChannel` en Android.
 * - [label]: etiqueta visible en la UI (Estable / Nocturno).
 */
enum class EngineChannel(
    val wire: String,
    val ytdlpValue: String,
    val label: String,
) {
    STABLE("STABLE", "stable", "Estable"),
    NIGHTLY("NIGHTLY", "nightly", "Nocturno");

    companion object {
        fun fromWire(value: String?): EngineChannel =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: STABLE
    }
}
