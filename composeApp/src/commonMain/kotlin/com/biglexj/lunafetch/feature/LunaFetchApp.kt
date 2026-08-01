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
import com.biglexj.lunafetch.feature.download.DownloadOptionsCard
import com.biglexj.lunafetch.feature.download.DownloadStatusCard
import com.biglexj.lunafetch.feature.download.LinkCard
import com.biglexj.lunafetch.feature.download.QuickDownloadSheet
import com.biglexj.lunafetch.feature.download.VideoCard
import com.biglexj.lunafetch.feature.header.AppHeader
import com.biglexj.lunafetch.feature.history.HistoryCard
import com.biglexj.lunafetch.feature.logs.LogsCard
import com.biglexj.lunafetch.feature.update.UpdateBanner
import com.biglexj.lunafetch.feature.update.UpdateModalDialog

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
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AppHeader(themeMode, onThemeSelected = { themeMode = it }, platform = platform, presenter = presenter, state = state)
                    UpdateBanner(state, presenter)
                    UpdateModalDialog(state, presenter, platform)
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
                // Floating Top Toast Notification
                val toastMsg = state.toastMessage
                if (toastMsg != null) {
                    LaunchedEffect(toastMsg) {
                        kotlinx.coroutines.delay(4000L)
                        presenter.clearToast()
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = toastMsg != null,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .padding(top = 16.dp),
                ) {
                    if (toastMsg != null) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shadowElevation = 6.dp,
                        ) {
                            androidx.compose.material3.Text(
                                text = toastMsg,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
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
