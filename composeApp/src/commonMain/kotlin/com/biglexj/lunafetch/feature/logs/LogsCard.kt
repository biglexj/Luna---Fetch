package com.biglexj.lunafetch.feature.logs

import com.biglexj.lunafetch.feature.components.LunaCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.DiagnosticStore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun LogsCard(state: LunaFetchState, presenter: LunaFetchPresenter? = null) {
    val hasLogs = state.logs.isNotEmpty()
    val hasError = !state.error.isNullOrBlank()
    val hasDiagnostics = state.diagnostics.isNotEmpty()

    if (!hasLogs && !hasError && !hasDiagnostics) return

    var expandedLiveLogs by remember(hasError) { mutableStateOf(hasError) }
    var expandedDiagnostics by remember { mutableStateOf(false) }
    var copiedReport by remember { mutableStateOf(false) }
    var copiedLive by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    val liveLogContent = if (hasLogs) state.logs.joinToString("\n") else state.error.orEmpty()

    fun copyLive() {
        @Suppress("DEPRECATION")
        clipboard.setText(AnnotatedString(liveLogContent))
        copiedLive = true
    }

    fun copyFullDiagnosticReport() {
        val report = DiagnosticStore.formatAllForReport(state.diagnostics)
        @Suppress("DEPRECATION")
        clipboard.setText(AnnotatedString(report))
        copiedReport = true
    }

    LunaCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.titleMedium)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Detalles del error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                state.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Sección de Registro en Vivo
            if (hasLogs || hasError) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Registro de la sesión actual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { copyLive() }) {
                            Text(if (copiedLive) "✓ Copiado" else "📋 Copiar", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { expandedLiveLogs = !expandedLiveLogs }) {
                            Text(if (expandedLiveLogs) "Ocultar" else "Ver detalles (${state.logs.size})")
                        }
                    }
                }

                if (expandedLiveLogs) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        liveLogContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Sección de Diagnósticos Persistentes (48 horas)
            if (hasDiagnostics) {
                if (hasLogs || hasError) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Diagnósticos de fallos (48h)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${state.diagnostics.size} incidente(s) conservados para soporte",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { copyFullDiagnosticReport() }) {
                            Text(
                                if (copiedReport) "✓ Reporte copiado" else "📋 Copiar reporte 48h",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(onClick = { expandedDiagnostics = !expandedDiagnostics }) {
                            Text(if (expandedDiagnostics) "Ocultar" else "Ver lista")
                        }
                        if (presenter != null) {
                            TextButton(onClick = { presenter.clearDiagnostics() }) {
                                Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (expandedDiagnostics) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.diagnostics.forEachIndexed { index, entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "Incidente #${index + 1} · ${entry.operation}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            entry.originDevice.ifBlank { "Local" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (entry.url.isNotBlank()) {
                                        Text(
                                            "URL: ${entry.url}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    Text(
                                        entry.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (entry.technicalDetails.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            entry.technicalDetails.take(500),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
