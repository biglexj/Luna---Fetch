package com.biglexj.lunafetch.domain

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticEntry(
    val id: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val url: String = "",
    val operation: String = "DOWNLOAD", // "DOWNLOAD" o "ANALYZE"
    val errorMessage: String,
    val technicalDetails: String = "",
    val originDevice: String = "",
) {
    companion object {
        // Ventana mínima de 48 horas de retención obligatoria (48 horas = 172,800,000 ms)
        const val RETENTION_MS: Long = 48L * 60 * 60 * 1000
    }

    fun isExpired(currentTimeMs: Long = System.currentTimeMillis()): Boolean =
        (currentTimeMs - timestampMs) > RETENTION_MS
}

object DiagnosticStore {
    /**
     * Purga las entradas cuya antigüedad sea superior a 48 horas.
     */
    fun purgeExpired(
        entries: List<DiagnosticEntry>,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): List<DiagnosticEntry> {
        val cutoff = currentTimeMs - DiagnosticEntry.RETENTION_MS
        return entries.filter { it.timestampMs >= cutoff }
    }

    /**
     * Inserta un nuevo diagnóstico técnico garantizando la retención de las últimas 48 horas.
     */
    fun addEntry(
        current: List<DiagnosticEntry>,
        newEntry: DiagnosticEntry,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): List<DiagnosticEntry> {
        val purged = purgeExpired(current, currentTimeMs)
        return (listOf(newEntry) + purged)
            .distinctBy { it.id }
            .sortedByDescending { it.timestampMs }
            .take(50) // Máximo 50 entradas dentro de la ventana de 48h
    }

    /**
     * Formatea una entrada para exportación directa de soporte técnico.
     */
    fun formatEntryForReport(entry: DiagnosticEntry): String = buildString {
        appendLine("=== DIAGNÓSTICO LUNA FETCH [48H] ===")
        appendLine("ID: ${entry.id}")
        appendLine("Fecha/Hora (Timestamp): ${entry.timestampMs}")
        appendLine("Operación: ${entry.operation}")
        appendLine("URL: ${entry.url}")
        if (entry.originDevice.isNotBlank()) appendLine("Dispositivo: ${entry.originDevice}")
        appendLine("Error: ${entry.errorMessage}")
        if (entry.technicalDetails.isNotBlank()) {
            appendLine("--- DETALLES TÉCNICOS / REGISTRO ---")
            appendLine(entry.technicalDetails.trim())
        }
        appendLine("=====================================")
    }

    /**
     * Formatea todas las entradas disponibles de las últimas 48h en un solo bloque.
     */
    fun formatAllForReport(entries: List<DiagnosticEntry>): String = buildString {
        if (entries.isEmpty()) {
            appendLine("No hay diagnósticos registrados en las últimas 48 horas.")
            return@buildString
        }
        appendLine("=== REPORTE CONSOLIDADO DE ERRORES (ÚLTIMAS 48 HORAS) ===")
        appendLine("Total incidentes: ${entries.size}")
        appendLine()
        entries.forEach { entry ->
            appendLine(formatEntryForReport(entry))
            appendLine()
        }
    }
}
