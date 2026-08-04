package com.biglexj.lunafetch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.biglexj.lunafetch.domain.LunaFetchPresenter
import com.biglexj.lunafetch.feature.LunaFetchApp
import com.biglexj.lunafetch.platform.DesktopDownloadEngine
import com.biglexj.lunafetch.platform.DesktopPlatformBindings
import com.biglexj.lunafetch.platform.LunaSocketServer
import com.biglexj.lunafetch.platform.ModernTrayManager
import com.biglexj.lunafetch.platform.NativeMessagingHost
import lunafetch.composeapp.generated.resources.Res
import lunafetch.composeapp.generated.resources.luna_fetch_icon
import org.jetbrains.compose.resources.painterResource

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import com.biglexj.lunafetch.platform.AppSettings
import com.biglexj.lunafetch.platform.SingleInstanceLock

fun main(args: Array<String>) {
    // ── Native Messaging Host mode (launched by the browser) ────────────────
    if (args.contains("--native-host")) {
        NativeMessagingHost.run()
        return
    }

    // ── Single Instance Lock Guarantee (desktop_app_standards.md) ───────────
    if (!SingleInstanceLock.acquireOrBringToFront()) {
        kotlin.system.exitProcess(0)
    }

    // ── Normal GUI mode ─────────────────────────────────────────────────────
    val initialUrl = args.firstOrNull { it.startsWith("--download-url=") }
        ?.removePrefix("--download-url=")

    val isAutostart = args.contains("--autostart")

    application {
        val bindings = remember { DesktopPlatformBindings() }
        val presenter = remember(bindings) { LunaFetchPresenter(bindings) }
        val appSettings = remember { AppSettings() }
        var isVisible by remember { mutableStateOf(!isAutostart) }

        val icon = painterResource(Res.drawable.luna_fetch_icon)

        // ── Window State Persistence (desktop_app_standards.md Rule 5) ────────
        val initialPlacement = if (appSettings.windowIsMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
        val initialPosition = run {
            val px = appSettings.windowPositionX
            val py = appSettings.windowPositionY
            if (px != null && py != null) {
                val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
                if (px in 0..(screenSize.width - 100) && py in 0..(screenSize.height - 100)) {
                    WindowPosition(px.dp, py.dp)
                } else {
                    WindowPosition(androidx.compose.ui.Alignment.Center)
                }
            } else {
                WindowPosition(androidx.compose.ui.Alignment.Center)
            }
        }
        val windowState = rememberWindowState(
            placement = initialPlacement,
            position = initialPosition,
            width = appSettings.windowWidth.dp,
            height = appSettings.windowHeight.dp,
        )

        fun saveWindowState() {
            if (windowState.placement == WindowPlacement.Maximized) {
                appSettings.windowIsMaximized = true
            } else {
                appSettings.windowIsMaximized = false
                appSettings.windowWidth = windowState.size.width.value.toInt()
                appSettings.windowHeight = windowState.size.height.value.toInt()
                val pos = windowState.position
                if (pos is WindowPosition.Absolute) {
                    appSettings.windowPositionX = pos.x.value.toInt()
                    appSettings.windowPositionY = pos.y.value.toInt()
                }
            }
        }

        // Listen for focus requests from duplicate launches to unminimize/bring app window to front
        remember {
            SingleInstanceLock.listenForFocusRequests {
                isVisible = true
            }
        }

        // Start local socket server so the browser extension can query qualities and trigger silent downloads
        remember {
            LunaSocketServer(
                onDownloadRequest = { url, format, quality, cookieFile ->
                    presenter.startDirectDownload(url, format, quality)
                },
                onAnalyzeRequest = { url, cookieFile ->
                    val info = (bindings.engine as? DesktopDownloadEngine)?.analyzeWithCookieFile(url, cookieFile)
                        ?: bindings.engine.analyze(url)
                    val vq = com.biglexj.lunafetch.domain.FormatCatalog.qualities(com.biglexj.lunafetch.domain.MediaFormat.Mp4, info.maxHeight)
                    val aq = com.biglexj.lunafetch.domain.FormatCatalog.qualities(com.biglexj.lunafetch.domain.MediaFormat.Mp3, info.maxHeight)
                    vq to aq
                }
            ).also { it.start() }
        }

        // Modern System Tray Menu with Fluent dark theme & turquoise hover accents
        remember {
            val trayImage = runCatching {
                val bytes = kotlinx.coroutines.runBlocking { Res.readBytes("drawable/luna_fetch_icon.png") }
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            }.getOrNull() ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)

            ModernTrayManager.setupTray(
                image = trayImage,
                tooltip = "Luna Fetch",
                onOpenApp = { isVisible = true },
                onOpenDownloadsFolder = { bindings.openOutput(bindings.defaultDestination) },
                onQuitApp = {
                    saveWindowState()
                    SingleInstanceLock.release()
                    exitApplication()
                },
            )
        }

        Window(
            onCloseRequest = {
                saveWindowState()
                if (bindings.isMinimizeToTrayEnabled == true) isVisible = false else {
                    SingleInstanceLock.release()
                    exitApplication()
                }
            },
            title = "Luna Fetch",
            icon = icon,
            state = windowState,
            visible = isVisible,
        ) {
            LunaFetchApp(
                platform = bindings,
                presenter = presenter,
                quickDownloadUrl = initialUrl,
            )
        }
    }
}
