package com.biglexj.lunafetch

import androidx.compose.runtime.LaunchedEffect
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
import com.biglexj.lunafetch.domain.synapse.SynapseAction
import com.biglexj.lunafetch.domain.synapse.SynapseUriParser
import com.biglexj.lunafetch.platform.AppSettings
import com.biglexj.lunafetch.platform.SingleInstanceLock
import com.biglexj.lunafetch.platform.synapse.SynapseDispatcherServer

fun main(args: Array<String>) {
    // ── Native Messaging Host mode (launched by the browser) ────────────────
    if (args.contains("--native-host")) {
        NativeMessagingHost.run()
        return
    }

    // ── Single Instance Lock & Aurora Synapse Dispatch Guarantee ────────────
    if (!SingleInstanceLock.acquireOrTransfer(args)) {
        kotlin.system.exitProcess(0)
    }

    // ── Normal GUI mode / First Instance ────────────────────────────────────
    val directUri = args.firstOrNull { it.startsWith("luna://", true) || it.startsWith("aurora-synapse://", true) }
    val initialUrl = args.firstOrNull { it.startsWith("--download-url=") }?.removePrefix("--download-url=")
    val isAutostart = args.contains("--autostart")

    val initialSynapseAction = when {
        directUri != null -> SynapseUriParser.parse(directUri)
        args.contains("--synapse-action") -> {
            val idx = args.indexOf("--synapse-action")
            if (idx != -1 && idx + 1 < args.size) SynapseUriParser.parse(args[idx + 1]) else null
        }
        initialUrl != null -> SynapseAction.EnqueueDownload(url = initialUrl)
        else -> null
    }

    application {
        val bindings = remember { DesktopPlatformBindings() }
        val presenter = remember(bindings) { LunaFetchPresenter(bindings) }
        val appSettings = remember { AppSettings() }
        val isDev = SingleInstanceLock.isDevMode()
        var isVisible by remember { mutableStateOf(if (isDev) true else !isAutostart) }

        val appIcon = remember {
            runCatching {
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("composeResources/lunafetch.composeapp.generated.resources/drawable/luna_fetch_icon.png")
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/luna_fetch_icon.png")
                    ?: DesktopPlatformBindings::class.java.getResourceAsStream("/drawable/luna_fetch_icon.png")
                stream?.use { androidx.compose.ui.res.loadImageBitmap(it) }?.let {
                    androidx.compose.ui.graphics.painter.BitmapPainter(it)
                }
            }.getOrNull()
        }

        // ── Window State Persistence (desktop_app_standards.md Rule 5) ────────
        val initialPlacement = if (appSettings.windowIsMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
        val initialPosition = run {
            val px = appSettings.windowPositionX
            val py = appSettings.windowPositionY
            if (px != null && py != null) {
                val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
                if (px in 0..(screenSize.width - 200) && py in 0..(screenSize.height - 200)) {
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

        LaunchedEffect(Unit) {
            println("[LunaFetch] Window visible = $isVisible, placement = $initialPlacement, isDev = $isDev")
        }

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

        // ── Aurora Synapse Dispatcher Server (127.0.0.1:49282) ───────────────
        val synapseServer = remember {
            SynapseDispatcherServer(
                port = SynapseDispatcherServer.SYNAPSE_PORT,
                onActionReceived = { action ->
                    presenter.handleSynapseAction(action)
                },
                onBringToFront = {
                    isVisible = true
                },
            ).also { it.startListening() }
        }

        // Listen for legacy lock requests (127.0.0.1:51235)
        remember {
            SingleInstanceLock.listenForLegacyRequests { payload ->
                val action = SynapseUriParser.parse(payload) ?: SynapseAction.Focus
                isVisible = true
                presenter.handleSynapseAction(action)
            }
        }

        // Process initial action if launched with URI or Synapse argument
        LaunchedEffect(initialSynapseAction) {
            initialSynapseAction?.let { action ->
                presenter.handleSynapseAction(action)
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
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("composeResources/lunafetch.composeapp.generated.resources/drawable/luna_fetch_icon.png")
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/luna_fetch_icon.png")
                    ?: DesktopPlatformBindings::class.java.getResourceAsStream("/drawable/luna_fetch_icon.png")
                if (stream != null) {
                    stream.use { javax.imageio.ImageIO.read(it) }
                } else null
            }.getOrNull() ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                g.color = java.awt.Color(0x06, 0xB6, 0xD4)
                g.fillOval(2, 2, 12, 12)
                g.dispose()
            }

            ModernTrayManager.setupTray(
                image = trayImage,
                tooltip = "Luna Fetch",
                onOpenApp = { isVisible = true },
                onOpenDownloadsFolder = { bindings.openOutput(bindings.defaultDestination) },
                onQuitApp = {
                    saveWindowState()
                    synapseServer.stop()
                    SingleInstanceLock.release()
                    exitApplication()
                },
            )
        }

        Window(
            onCloseRequest = {
                saveWindowState()
                if (bindings.isMinimizeToTrayEnabled == true) isVisible = false else {
                    synapseServer.stop()
                    SingleInstanceLock.release()
                    exitApplication()
                }
            },
            title = if (isDev) "Luna Fetch [Dev]" else "Luna Fetch",
            icon = appIcon,
            state = windowState,
            visible = isVisible,
        ) {
            androidx.compose.runtime.DisposableEffect(window) {
                runCatching {
                    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("composeResources/lunafetch.composeapp.generated.resources/drawable/luna_fetch_icon.png")
                        ?: Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/luna_fetch_icon.png")
                        ?: DesktopPlatformBindings::class.java.getResourceAsStream("/drawable/luna_fetch_icon.png")
                    stream?.use { javax.imageio.ImageIO.read(it) }?.let { window.iconImage = it }
                }
                if (isDev) {
                    java.awt.EventQueue.invokeLater {
                        window.isMinimized = false
                        window.toFront()
                        window.requestFocus()
                        runCatching {
                            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getWindowPointer(window))
                            com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
                        }
                    }
                }
                onDispose { }
            }

            LunaFetchApp(
                platform = bindings,
                presenter = presenter,
                quickDownloadUrl = initialUrl,
            )
        }
    }
}
