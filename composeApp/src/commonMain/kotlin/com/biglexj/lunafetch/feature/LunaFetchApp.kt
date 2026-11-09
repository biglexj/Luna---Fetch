package com.biglexj.lunafetch.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biglexj.lunafetch.core.theme.LunaFetchTheme
import com.biglexj.lunafetch.core.theme.ThemeMode
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.domain.LunaFetchState
import com.biglexj.lunafetch.domain.PlatformBindings
import com.biglexj.lunafetch.feature.download.*
import com.biglexj.lunafetch.feature.header.SettingsScreen
import com.biglexj.lunafetch.feature.header.NavigationGlyph
import com.biglexj.lunafetch.feature.header.LunaGlyph
import com.biglexj.lunafetch.feature.history.HistoryCard
import com.biglexj.lunafetch.feature.logs.DownloadErrorCard
import com.biglexj.lunafetch.feature.logs.LogsCard
import com.biglexj.lunafetch.feature.update.UpdateBanner
import com.biglexj.lunafetch.feature.update.UpdateModalDialog

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.biglexj.lunafetch.domain.QueueItemStatus

private enum class AppPage(val label: String, val glyph: LunaGlyph) {
    Downloads("Descargas", LunaGlyph.Download),
    Queue("Cola", LunaGlyph.Queue),
    History("Historial", LunaGlyph.History),
    Settings("Ajustes", LunaGlyph.Settings),
}

@Composable
fun LunaFetchApp(
    platform: PlatformBindings,
    presenter: LunaFetchPresenter = remember(platform) { LunaFetchPresenter(platform) },
    quickDownloadUrl: String? = null,
    onDismissQuickDownload: () -> Unit = {},
) {
    val state by presenter.state.collectAsState()
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var page by remember { mutableStateOf(AppPage.Downloads) }
    LunaFetchTheme(themeMode) {
        if (quickDownloadUrl != null) {
            LaunchedEffect(quickDownloadUrl) { presenter.setUrl(quickDownloadUrl); presenter.analyze() }
            QuickDownloadSheet(state, presenter, platform, onDismissQuickDownload)
            return@LunaFetchTheme
        }
        LaunchedEffect(Unit) { presenter.refreshHistory() }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.toastMessage) {
            state.toastMessage?.let { snackbar.showSnackbar(it); presenter.clearToast() }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val rail = maxWidth >= 760.dp
                Row(Modifier.fillMaxSize()) {
                    if (rail) NavigationRail(modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Box(Modifier.width(80.dp).fillMaxHeight()) {
                            ThemeToggle(
                                mode = themeMode,
                                onCycle = {
                                    themeMode = when (themeMode) {
                                        ThemeMode.System -> ThemeMode.Light
                                        ThemeMode.Light -> ThemeMode.Dark
                                        ThemeMode.Dark -> ThemeMode.System
                                    }
                                },
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                            )
                            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                listOf(AppPage.Downloads, AppPage.Queue, AppPage.History).forEach { item ->
                                    val badgeCount = if (item == AppPage.Queue) {
                                        state.queue.count { it.status == QueueItemStatus.Pending || it.status == QueueItemStatus.Running }
                                    } else 0
                                    NavigationRailItem(
                                        selected = page == item,
                                        onClick = { page = item },
                                        icon = {
                                            if (badgeCount > 0) {
                                                BadgedBox(badge = {
                                                    Badge {
                                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                                    }
                                                }) {
                                                    NavigationGlyph(item.glyph)
                                                }
                                            } else {
                                                NavigationGlyph(item.glyph)
                                            }
                                        },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                            NavigationRailItem(selected = page == AppPage.Settings, onClick = { page = AppPage.Settings },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                                icon = { NavigationGlyph(LunaGlyph.Settings) },
                                label = { Text("Ajustes", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = {
                            SnackbarHost(snackbar) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(16.dp),
                                )
                            }
                        },
                        bottomBar = {
                            if (!rail) NavigationBar {
                                AppPage.entries.forEach { item ->
                                    val badgeCount = if (item == AppPage.Queue) {
                                        state.queue.count { it.status == QueueItemStatus.Pending || it.status == QueueItemStatus.Running }
                                    } else 0
                                    NavigationBarItem(
                                        selected = page == item,
                                        onClick = { page = item },
                                        icon = {
                                            if (badgeCount > 0) {
                                                BadgedBox(badge = {
                                                    Badge {
                                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                                    }
                                                }) {
                                                    NavigationGlyph(item.glyph)
                                                }
                                            } else {
                                                NavigationGlyph(item.glyph)
                                            }
                                        },
                                        label = { Text(item.label, maxLines = 1) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            UpdateBanner(state, presenter)
                            when (page) {
                                AppPage.Downloads -> DownloadsScreen(state, presenter, platform, onNavigateToQueue = { page = AppPage.Queue })
                                AppPage.Queue -> Column(
                                    Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    Text("Cola", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    QueueCard(state, presenter, emptyFallback = true)
                                }
                                AppPage.History -> Column(Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    HistoryCard(state, presenter, platform)
                                }
                                AppPage.Settings -> SettingsScreen(platform, presenter, themeMode) { themeMode = it }
                            }
                        }
                    }
                }
                UpdateModalDialog(state, presenter, platform)
            }
        }
    }
}

/**
 * Botón de tema en la barra lateral.
 *
 * El tema tiene tres estados (automático, claro y oscuro) y cada uno tiene su ícono:
 * disco medio, sol y luna. Cada pulsación avanza al siguiente estado.
 */
@Composable
private fun ThemeToggle(mode: ThemeMode, onCycle: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onCycle),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NavigationGlyph(
                    glyph = when (mode) {
                        ThemeMode.System -> LunaGlyph.Auto
                        ThemeMode.Light -> LunaGlyph.Sun
                        ThemeMode.Dark -> LunaGlyph.Moon
                    },
                    background = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        }
        Text("Tema", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DownloadsScreen(
    state: LunaFetchState,
    presenter: LunaFetchPresenter,
    platform: PlatformBindings,
    onNavigateToQueue: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 860.dp
        val gutter = if (maxWidth < 600.dp) 16.dp else 24.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(gutter),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Descargas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    LinkCard(state, presenter, platform)
                    QueueSummaryCard(state, onNavigateToQueue)
                    VideoCard(state, presenter)
                    // El resultado de la descarga (completada o fallida) queda
                    // al final, debajo de todo lo que se puede seguir acciones.
                    DownloadStatusCard(state, presenter, platform)
                    DownloadErrorCard(state)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    DownloadOptionsCard(state, presenter, platform)
                    LogsCard(state, presenter)
                }
            } else {
                LinkCard(state, presenter, platform)
                QueueSummaryCard(state, onNavigateToQueue)
                VideoCard(state, presenter)
                DownloadStatusCard(state, presenter, platform)
                DownloadErrorCard(state)
                DownloadOptionsCard(state, presenter, platform)
                LogsCard(state, presenter)
            }
        }
    }
}
