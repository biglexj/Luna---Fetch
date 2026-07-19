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

fun main(args: Array<String>) {
    // ── Native Messaging Host mode (launched by the browser) ────────────────
    if (args.contains("--native-host")) {
        NativeMessagingHost.run()
        return
    }

    // ── Normal GUI mode ─────────────────────────────────────────────────────
    val initialUrl = args.firstOrNull { it.startsWith("--download-url=") }
        ?.removePrefix("--download-url=")

    val isAutostart = args.contains("--autostart")

    application {
        val bindings = remember { DesktopPlatformBindings() }
        val presenter = remember(bindings) { LunaFetchPresenter(bindings) }
        var isVisible by remember { mutableStateOf(!isAutostart) }

        val icon = painterResource(Res.drawable.luna_fetch_icon)

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
                onQuitApp = ::exitApplication,
            )
        }

        Window(
            onCloseRequest = {
                if (bindings.isMinimizeToTrayEnabled == true) isVisible = false else exitApplication()
            },
            title = "Luna Fetch",
            icon = icon,
            state = rememberWindowState(width = 1040.dp, height = 780.dp),
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
