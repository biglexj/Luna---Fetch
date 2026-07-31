package com.biglexj.lunafetch.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings

@Composable
fun UpdateModalDialog(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
) {
    if (!state.showUpdateModal) return
    val release = state.availableUpdate ?: return

    Dialog(
        onDismissRequest = { presenter.dismissUpdateModal() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isUpdateDownloading,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .widthIn(max = 480.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.updateDownloadedFilePath != null) "✅" else "🚀",
                        fontSize = 26.sp,
                    )
                }

                // Title & Subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.updateDownloadedFilePath != null) "¡Descarga Lista!" else "Actualizar Luna Fetch",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Nueva versión v${release.version} disponible",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Scrollable Release Notes with Clean Formatting
                val cleanBody = renderCleanReleaseNotes(release.body)
                if (cleanBody.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = "Novedades de esta versión:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = cleanBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                // Progress Indicator / Status Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.isUpdateDownloading) {
                        val percentage = (state.updateDownloadProgress * 100).toInt().coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Descargando paquete...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { state.updateDownloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else if (state.updateDownloadedFilePath != null) {
                        Text(
                            text = "El paquete se descargó correctamente. Presiona instalar para aplicar la actualización.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (state.updateError != null) {
                        Text(
                            text = state.updateError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isUpdateDownloading) {
                        OutlinedButton(
                            onClick = { presenter.dismissUpdateModal() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("Cancelar", maxLines = 1)
                        }
                    } else if (state.updateDownloadedFilePath != null) {
                        OutlinedButton(
                            onClick = { presenter.dismissUpdateModal() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("Cerrar", maxLines = 1, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { presenter.installDownloadedUpdate() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Instalar", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else if (state.updateError != null) {
                        OutlinedButton(
                            onClick = { presenter.dismissUpdateModal() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("Cerrar", maxLines = 1, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { presenter.startUpdateDownload() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("Reintentar", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { presenter.dismissUpdateModal() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("Ahora no", maxLines = 1, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { presenter.startUpdateDownload() },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Descargar", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun renderCleanReleaseNotes(rawMarkdown: String): String {
    if (rawMarkdown.isBlank()) return ""
    return rawMarkdown.lineSequence()
        .map { line ->
            var cleaned = line.trim()
            if (cleaned.startsWith("#")) {
                cleaned = cleaned.trimStart('#', ' ').trim()
            }
            if (cleaned.startsWith("- ") || cleaned.startsWith("* ")) {
                cleaned = "• " + cleaned.substring(2).trim()
            }
            cleaned = cleaned.replace("**", "").replace("__", "").replace("*", "")
            cleaned
        }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}
