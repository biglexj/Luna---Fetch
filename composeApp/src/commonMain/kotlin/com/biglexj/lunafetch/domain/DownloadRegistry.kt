package com.biglexj.lunafetch.domain

import kotlinx.serialization.Serializable

/**
 * Estado de un archivo en el sistema de archivos local.
 */
enum class LocalFileState {
    /** El archivo no existe: fue movido o eliminado. */
    Missing,

    /** El archivo existe pero está vacío: la descarga quedó incompleta o dañada. */
    Empty,

    /** El archivo existe y tiene contenido. */
    Present,
}

/**
 * Registro persistente de las descargas ya realizadas.
 *
 * El historial visible está limitado a los últimos elementos, así que este
 * registro aparte conserva el mapa enlace → archivo durante más tiempo. Sirve
 * para reconocer duplicados y, sobre todo, para distinguir un archivo movido
 * de uno dañado.
 */
@Serializable
data class DownloadRegistryEntry(
    /** Clave canónica del enlace (ver [LinkCanonicalizer]). */
    val canonicalKey: String,
    /** Familia de formato (mp4, mp3, webm...). */
    val formatKey: String,
    /** Etiqueta legible del formato y la calidad. */
    val formatLabel: String = "",
    /** Ruta del archivo descargado. */
    val path: String,
    /** Título del contenido. */
    val title: String = "",
    /** Marca de tiempo de la descarga. */
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Operaciones sobre el registro de descargas.
 */
object DownloadRegistry {

    /** Número máximo de entradas conservadas. */
    const val MAX_ENTRIES = 200

    /**
     * Busca la descarga previa del mismo contenido y formato.
     *
     * La clave canónica agrupa los enlaces equivalentes y el formato separa
     * el video de su audio: el MP4 y el MP3 del mismo video son descargas
     * distintas.
     */
    fun find(
        entries: List<DownloadRegistryEntry>,
        canonicalKey: String,
        formatKey: String,
    ): DownloadRegistryEntry? = entries.firstOrNull {
        it.canonicalKey == canonicalKey && it.formatKey == formatKey
    }

    /** Inserta o actualiza una entrada, conservando las más recientes. */
    fun upsert(
        entries: List<DownloadRegistryEntry>,
        entry: DownloadRegistryEntry,
    ): List<DownloadRegistryEntry> {
        val filtered = entries.filterNot {
            it.canonicalKey == entry.canonicalKey && it.formatKey == entry.formatKey
        }
        return (listOf(entry) + filtered).take(MAX_ENTRIES)
    }
}

/**
 * Carpeta que contiene al archivo.
 *
 * @return La carpeta o una cadena vacía si la ruta no corresponde a un archivo
 * del sistema de archivos (por ejemplo, un URI `content://` de Android).
 */
fun parentFolderOf(path: String): String {
    if (path.isBlank() || path.startsWith("content://")) return ""
    val index = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    if (index <= 0) return ""
    return path.substring(0, index)
}
