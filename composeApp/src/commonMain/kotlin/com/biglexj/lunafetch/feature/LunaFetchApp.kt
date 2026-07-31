package com.biglexj.lunafetch.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.core.theme.LunaFetchTheme
import com.biglexj.lunafetch.core.theme.ThemeMode
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.feature.components.AppHeader
import com.biglexj.lunafetch.feature.components.DownloadOptionsCard
import com.biglexj.lunafetch.feature.components.DownloadStatusCard
import com.biglexj.lunafetch.feature.components.HistoryCard
import com.biglexj.lunafetch.feature.components.LinkCard
import com.biglexj.lunafetch.feature.components.LogsCard
import com.biglexj.lunafetch.feature.components.QuickDownloadSheet
import com.biglexj.lunafetch.feature.components.VideoCard

@Composable
fun LunaFetchApp(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter = remember(platform) { LunaFetchPresenter(platform) },
    quickDownloadUrl: String? = null,
    onDismissQuickDownload: () -> Unit = {},
) {
    val state by presenter.state.collectAsState()
    var themeMode by remember { mutableStateOf(ThemeMode.System) }

    LunaFetchTheme(themeMode) {
        if (quickDownloadUrl != null) {
            LaunchedEffect(quickDownloadUrl) {
                presenter.setUrl(quickDownloadUrl)
                presenter.analyze()
            }
            QuickDownloadSheet(state, presenter, onDismissQuickDownload)
            return@LunaFetchTheme
        }
        LaunchedEffect(Unit) {
            presenter.refreshHistory()
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                AppHeader(themeMode, onThemeSelected = { themeMode = it }, platform = platform, presenter = presenter, state = state)
                com.biglexj.lunafetch.feature.components.UpdateBanner(state, presenter)
                com.biglexj.lunafetch.feature.components.UpdateModalDialog(state, presenter, platform)
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val compact = maxWidth < 720.dp
                    val scroll = rememberScrollState()
                    if (compact) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            MainCards(state, presenter, platform)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(scroll),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                LinkCard(state, presenter)
                                VideoCard(state, presenter)
                                HistoryCard(state, presenter, platform)
                            }
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                DownloadOptionsCard(state, presenter, platform)
                                DownloadStatusCard(state, presenter)
                                LogsCard(state)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainCards(state: LunaFetchState, presenter: LunaFetchPresenter, platform: PlatformBindings) {
    LinkCard(state, presenter)
    VideoCard(state, presenter)
    DownloadOptionsCard(state, presenter, platform)
    DownloadStatusCard(state, presenter)
    HistoryCard(state, presenter, platform)
    LogsCard(state)
    Spacer(Modifier.height(8.dp))
}
