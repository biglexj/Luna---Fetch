package com.biglexj.lunafetch.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder

enum class TrayIconType { APP, SETTINGS, FOLDER, EXIT }

object ModernTrayManager {

    private fun isSystemDarkMode(): Boolean {
        return runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"))
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            text.contains("0x0")
        }.getOrDefault(false)
    }

    fun setupTray(
        image: Image,
        tooltip: String = "Luna Fetch",
        onOpenApp: () -> Unit,
        onOpenDownloadsFolder: () -> Unit,
        onQuitApp: () -> Unit,
    ): TrayIcon? {
        if (!SystemTray.isSupported()) return null

        // JDialog with POPUP type: OS treats it as a real popup, focus events work correctly
        val dialog = JDialog()
        dialog.isUndecorated = true
        dialog.isAlwaysOnTop = true
        dialog.background = Color(0, 0, 0, 0)
        dialog.type = Window.Type.POPUP

        // Guard: ignore the very first windowLostFocus that fires right after showing
        // (happens because the tray icon is in a native OS thread)
        var justOpened = false

        dialog.addWindowFocusListener(object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent) {}
            override fun windowLostFocus(e: WindowEvent) {
                if (!justOpened) {
                    dialog.isVisible = false
                }
            }
        })

        val itemWidth = 150
        val itemHeight = 32

        val mainPanel = object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = EmptyBorder(6, 6, 6, 6)
                isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val isDark = isSystemDarkMode()
                g2.color = if (isDark) Color(0x1a, 0x1b, 0x26) else Color(0xf4, 0xf4, 0xf6)
                g2.fillRoundRect(0, 0, width, height, 12, 12)
                g2.color = if (isDark) Color(0x2e, 0x30, 0x46) else Color(0xe1, 0xe3, 0xe8)
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
                g2.dispose()
                super.paintComponent(g)
            }
        }

        fun createItem(text: String, iconType: TrayIconType, onClick: () -> Unit): JPanel {
            return object : JPanel() {
                private var isHovered = false

                init {
                    layout = null
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    minimumSize = Dimension(itemWidth, itemHeight)
                    preferredSize = Dimension(itemWidth, itemHeight)
                    maximumSize = Dimension(itemWidth, itemHeight)

                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) { isHovered = true; repaint() }
                        override fun mouseExited(e: MouseEvent) { isHovered = false; repaint() }
                        override fun mouseClicked(e: MouseEvent) {
                            dialog.isVisible = false
                            onClick()
                        }
                    })
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                    val isDark = isSystemDarkMode()
                    val textColor: Color
                    val iconColor: Color

                    if (isHovered) {
                        g2.color = Color(0x1a, 0x8a, 0x6e)
                        g2.fillRoundRect(2, 1, width - 4, height - 2, 8, 8)
                        textColor = Color.WHITE
                        iconColor = Color.WHITE
                    } else {
                        textColor = if (isDark) Color(0xf1, 0xf1, 0xf1) else Color(0x33, 0x33, 0x33)
                        iconColor = if (isDark) Color(0x8a, 0x8d, 0xa0) else Color(0x55, 0x55, 0x55)
                    }

                    drawVectorIcon(g2, iconType, 10, (height - 16) / 2, iconColor)

                    g2.font = Font("Segoe UI", Font.PLAIN, 12)
                    g2.color = textColor
                    val fm = g2.fontMetrics
                    val textY = (height - fm.height) / 2 + fm.ascent
                    g2.drawString(text, 30, textY)
                    g2.dispose()
                }
            }
        }

        mainPanel.add(createItem("Abrir Luna Fetch", TrayIconType.SETTINGS, onOpenApp))
        mainPanel.add(createItem("Ver descargas", TrayIconType.FOLDER, onOpenDownloadsFolder))
        mainPanel.add(object : JSeparator() {
            init {
                alignmentX = Component.LEFT_ALIGNMENT
                minimumSize = Dimension(itemWidth, 2)
                preferredSize = Dimension(itemWidth, 2)
                maximumSize = Dimension(itemWidth, 2)
                border = EmptyBorder(2, 4, 2, 4)
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                val isDark = isSystemDarkMode()
                g2.color = if (isDark) Color(0x2e, 0x30, 0x46) else Color(0xe1, 0xe3, 0xe8)
                g2.drawLine(4, 1, width - 8, 1)
                g2.dispose()
            }
        })
        mainPanel.add(createItem("Salir", TrayIconType.EXIT, onQuitApp))

        dialog.contentPane = mainPanel
        dialog.pack()

        val trayIcon = TrayIcon(image, tooltip).apply {
            isImageAutoSize = true
        }

        fun showWindowAtTray(x: Int, y: Int) {
            if (dialog.isVisible) return  // already open, do nothing

            mainPanel.repaint()
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            var wx = x
            var wy = y - dialog.height - 10
            if (wx + dialog.width > screenSize.width) wx = screenSize.width - dialog.width - 10
            if (wy < 0) wy = y + 10

            // Set guard so the initial tray focus-lost event doesn't immediately close
            justOpened = true
            dialog.location = Point(wx, wy)
            dialog.isVisible = true
            dialog.toFront()
            dialog.requestFocus()

            // After 300ms, allow windowLostFocus to close the dialog normally
            Timer(300) { justOpened = false }.apply { isRepeats = false; start() }
        }

        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // Left click: open app
                if (e.button == MouseEvent.BUTTON1) {
                    dialog.isVisible = false
                    onOpenApp()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                // Right click: show menu (only on release, not on press, to avoid double-trigger)
                if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) {
                    val mx = e.x
                    val my = e.y
                    SwingUtilities.invokeLater { showWindowAtTray(mx, my) }
                }
            }
        })

        runCatching {
            SystemTray.getSystemTray().add(trayIcon)
        }

        return trayIcon
    }

    private fun drawVectorIcon(g2: Graphics2D, type: TrayIconType, x: Int, y: Int, color: Color) {
        g2.color = color
        g2.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        when (type) {
            TrayIconType.APP -> g2.drawArc(x + 2, y + 2, 11, 11, 40, 280)
            TrayIconType.SETTINGS -> {
                g2.drawOval(x + 4, y + 4, 8, 8)
                g2.drawOval(x + 6, y + 6, 4, 4)
                g2.drawLine(x + 8, y + 1, x + 8, y + 3)
                g2.drawLine(x + 8, y + 13, x + 8, y + 15)
                g2.drawLine(x + 1, y + 8, x + 3, y + 8)
                g2.drawLine(x + 13, y + 8, x + 15, y + 8)
            }
            TrayIconType.FOLDER -> {
                g2.drawRoundRect(x + 1, y + 4, 13, 9, 2, 2)
                g2.drawLine(x + 1, y + 4, x + 5, y + 4)
                g2.drawLine(x + 5, y + 2, x + 8, y + 2)
            }
            TrayIconType.EXIT -> {
                g2.drawRoundRect(x + 1, y + 2, 8, 11, 2, 2)
                g2.drawLine(x + 6, y + 7, x + 14, y + 7)
                g2.drawLine(x + 11, y + 4, x + 14, y + 7)
                g2.drawLine(x + 11, y + 10, x + 14, y + 7)
            }
        }
    }
}
