package com.biglexj.lunafetch.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.feature.components.LunaCard

@Composable
fun LinkCard(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
) = LunaCard {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.url.isBlank()) {
            val clip = platform.readClipboardText()?.trim().orEmpty()
            if (clip.isNotBlank() && isYouTubeOrMediaUrl(clip)) {
                presenter.setUrl(clip)
                presenter.showToast("URL detectada del portapapeles")
            }
        }
    }

    Text("Enlace de descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.url,
            onValueChange = presenter::setUrl,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            enabled = !state.isAnalyzing && !state.isDownloading,
            singleLine = true,
            placeholder = if (!isFocused) {
                {
                    Text(
                        "Pega acá la URL (YouTube, TikTok, Instagram...)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            } else null,
            shape = RoundedCornerShape(20.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    val clip = platform.readClipboardText()?.trim().orEmpty()
                    if (clip.isNotBlank()) {
                        presenter.setUrl(clip)
                        presenter.showToast("URL pegada desde el portapapeles")
                    } else {
                        presenter.showToast("El portapapeles está vacío")
                    }
                },
                enabled = !state.isAnalyzing && !state.isDownloading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text("📋 Pegar", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = presenter::analyze,
                enabled = !state.isAnalyzing && !state.isDownloading && state.url.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(if (state.isAnalyzing) "Analizando…" else "Analizar", fontWeight = FontWeight.Bold)
            }
        }
    }

    state.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

private fun isYouTubeOrMediaUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        return false
    }
    val lower = trimmed.lowercase()
    return lower.contains("youtube.com") || lower.contains("youtu.be") ||
            lower.contains("tiktok.com") || lower.contains("instagram.com") ||
            lower.contains("x.com") || lower.contains("twitter.com") ||
            lower.contains("facebook.com") || lower.contains("fb.watch") ||
            lower.contains("vimeo.com") || lower.contains("twitch.tv")
}
