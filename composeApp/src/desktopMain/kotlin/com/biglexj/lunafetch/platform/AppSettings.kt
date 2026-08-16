package com.biglexj.lunafetch.platform

import java.io.File
import java.util.prefs.Preferences

/**
 * Desktop-only settings stored in Java Preferences (HKCU on Windows).
 * Each property reads/writes synchronously; values survive app restarts.
 */
class AppSettings {
    private val prefs = Preferences.userRoot().node("com/biglexj/lunafetch/settings")

    init {
        // Enforce autostart registry key on startup if enabled
        if (autoStart) {
            applyAutoStart(true)
        }
    }

    /** Minimise to system tray instead of quitting when the window is closed. */
    var minimizeToTray: Boolean
        get() = prefs.getBoolean("minimizeToTray", true)
        set(v) = prefs.putBoolean("minimizeToTray", v)

    /** Register (or remove) a Windows auto-start registry entry. */
    var autoStart: Boolean
        get() = prefs.getBoolean("autoStart", true)
        set(v) {
            prefs.putBoolean("autoStart", v)
            applyAutoStart(v)
        }

    /** Window dimensions, position and maximized state persistence (desktop_app_standards.md Rule 5). */
    var windowWidth: Int
        get() = prefs.getInt("windowWidth", 1040)
        set(v) = prefs.putInt("windowWidth", v.coerceAtLeast(600))

    var windowHeight: Int
        get() = prefs.getInt("windowHeight", 780)
        set(v) = prefs.putInt("windowHeight", v.coerceAtLeast(400))

    var windowIsMaximized: Boolean
        get() = prefs.getBoolean("windowIsMaximized", false)
        set(v) = prefs.putBoolean("windowIsMaximized", v)

    var windowPositionX: Int?
        get() {
            val raw = prefs.get("windowPositionX", null) ?: return null
            return raw.toIntOrNull()
        }
        set(v) {
            if (v != null) prefs.put("windowPositionX", v.toString()) else prefs.remove("windowPositionX")
        }

    var windowPositionY: Int?
        get() {
            val raw = prefs.get("windowPositionY", null) ?: return null
            return raw.toIntOrNull()
        }
        set(v) {
            if (v != null) prefs.put("windowPositionY", v.toString()) else prefs.remove("windowPositionY")
        }

    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    /** Whether the native messaging host manifest is installed for Chrome/Edge/Chromium. */
    val isNativeHostInstalled: Boolean
        get() {
            if (isWindows) {
                val key = "HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\com.biglexj.lunafetch"
                return runCatching {
                    val proc = Runtime.getRuntime().exec(arrayOf("reg", "query", key))
                    proc.waitFor() == 0
                }.getOrDefault(false)
            } else {
                val userHome = System.getProperty("user.home") ?: return false
                val chromeHost = File(userHome, ".config/google-chrome/NativeMessagingHosts/com.biglexj.lunafetch.json")
                val chromiumHost = File(userHome, ".config/chromium/NativeMessagingHosts/com.biglexj.lunafetch.json")
                return chromeHost.exists() || chromiumHost.exists()
            }
        }

    /**
     * Writes the native messaging host JSON manifest and registers it for browsers.
     *
     * @param exePath Absolute path to the host executable.
     */
    fun installNativeHost(exePath: String) {
        val userHome = System.getProperty("user.home") ?: "."
        val escapedExePath = if (isWindows) exePath.replace("\\", "\\\\") else exePath
        val manifestContent = """
            {
              "name": "com.biglexj.lunafetch",
              "description": "Luna Fetch native messaging host",
              "path": "$escapedExePath",
              "type": "stdio",
              "allowed_origins": [
                "chrome-extension://",
                "edge-extension://"
              ]
            }
        """.trimIndent()

        if (isWindows) {
            val manifestDir = File(System.getenv("APPDATA") ?: userHome, "LunaFetch")
            manifestDir.mkdirs()
            val manifest = File(manifestDir, "com.biglexj.lunafetch.json")
            manifest.writeText(manifestContent)

            for (browser in listOf("Google\\Chrome", "Microsoft\\Edge")) {
                val key = "HKCU\\Software\\$browser\\NativeMessagingHosts\\com.biglexj.lunafetch"
                Runtime.getRuntime().exec(
                    arrayOf("reg", "add", key, "/ve", "/t", "REG_SZ", "/d", manifest.absolutePath, "/f")
                )
            }
        } else {
            val targetDirs = listOf(
                File(userHome, ".config/google-chrome/NativeMessagingHosts"),
                File(userHome, ".config/chromium/NativeMessagingHosts"),
                File(userHome, ".config/BraveSoftware/Brave-Browser/NativeMessagingHosts"),
                File(userHome, ".config/microsoft-edge/NativeMessagingHosts")
            )
            for (dir in targetDirs) {
                dir.mkdirs()
                File(dir, "com.biglexj.lunafetch.json").writeText(manifestContent)
            }
        }
        prefs.putBoolean("nativeHostInstalled", true)
    }

    fun uninstallNativeHost() {
        if (isWindows) {
            for (browser in listOf("Google\\Chrome", "Microsoft\\Edge")) {
                val key = "HKCU\\Software\\$browser\\NativeMessagingHosts\\com.biglexj.lunafetch"
                runCatching { Runtime.getRuntime().exec(arrayOf("reg", "delete", key, "/f")) }
            }
        } else {
            val userHome = System.getProperty("user.home") ?: "."
            val targetDirs = listOf(
                File(userHome, ".config/google-chrome/NativeMessagingHosts"),
                File(userHome, ".config/chromium/NativeMessagingHosts"),
                File(userHome, ".config/BraveSoftware/Brave-Browser/NativeMessagingHosts"),
                File(userHome, ".config/microsoft-edge/NativeMessagingHosts")
            )
            for (dir in targetDirs) {
                File(dir, "com.biglexj.lunafetch.json").takeIf { it.exists() }?.delete()
            }
        }
        prefs.putBoolean("nativeHostInstalled", false)
    }

    private fun applyAutoStart(enable: Boolean) {
        runCatching {
            val exePath = ProcessHandle.current().info().command().orElse(null) ?: return
            // Ignore java runner during development
            if (exePath.endsWith("java.exe", ignoreCase = true) || exePath.endsWith("javaw.exe", ignoreCase = true) ||
                exePath.endsWith("/java", ignoreCase = true)) {
                return
            }
            if (isWindows) {
                val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
                val valueName = "LunaFetch"
                if (enable) {
                    Runtime.getRuntime().exec(
                        arrayOf("reg", "add", key, "/v", valueName, "/t", "REG_SZ", "/d", "\"$exePath\" --autostart", "/f")
                    )
                } else {
                    Runtime.getRuntime().exec(
                        arrayOf("reg", "delete", key, "/v", valueName, "/f")
                    )
                }
            } else {
                val userHome = System.getProperty("user.home") ?: return
                val autostartDir = File(userHome, ".config/autostart")
                val desktopFile = File(autostartDir, "lunafetch.desktop")
                if (enable) {
                    autostartDir.mkdirs()
                    desktopFile.writeText(
                        """
                        [Desktop Entry]
                        Type=Application
                        Name=Luna Fetch
                        Exec="$exePath" --autostart
                        Icon=lunafetch
                        Comment=Luna Fetch Media Downloader
                        Terminal=false
                        Categories=AudioVideo;Utility;
                        """.trimIndent()
                    )
                } else {
                    if (desktopFile.exists()) {
                        desktopFile.delete()
                    }
                }
            }
        }
    }
}
