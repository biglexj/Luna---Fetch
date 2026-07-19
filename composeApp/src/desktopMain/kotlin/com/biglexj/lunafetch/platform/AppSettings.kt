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

    /** Whether the native messaging host manifest is installed for Chrome/Edge. */
    val isNativeHostInstalled: Boolean
        get() {
            val key = "HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\com.biglexj.lunafetch"
            return runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("reg", "query", key))
                proc.waitFor() == 0
            }.getOrDefault(false)
        }

    /**
     * Writes the native messaging host JSON manifest and registers it in the
     * Windows registry for Chrome and Edge.
     *
     * @param exePath  Absolute path to LunaFetch.exe (the host executable).
     */
    fun installNativeHost(exePath: String) {
        val manifestDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "LunaFetch")
        manifestDir.mkdirs()
        val manifest = File(manifestDir, "com.biglexj.lunafetch.json")
        manifest.writeText("""
            {
              "name": "com.biglexj.lunafetch",
              "description": "Luna Fetch native messaging host",
              "path": "${ exePath.replace("\\", "\\\\") }",
              "type": "stdio",
              "allowed_origins": [
                "chrome-extension://",
                "edge-extension://"
              ]
            }
        """.trimIndent())

        for (browser in listOf("Google\\Chrome", "Microsoft\\Edge")) {
            val key = "HKCU\\Software\\$browser\\NativeMessagingHosts\\com.biglexj.lunafetch"
            Runtime.getRuntime().exec(
                arrayOf("reg", "add", key, "/ve", "/t", "REG_SZ", "/d", manifest.absolutePath, "/f")
            )
        }
        prefs.putBoolean("nativeHostInstalled", true)
    }

    fun uninstallNativeHost() {
        for (browser in listOf("Google\\Chrome", "Microsoft\\Edge")) {
            val key = "HKCU\\Software\\$browser\\NativeMessagingHosts\\com.biglexj.lunafetch"
            runCatching { Runtime.getRuntime().exec(arrayOf("reg", "delete", key, "/f")) }
        }
        prefs.putBoolean("nativeHostInstalled", false)
    }

    private fun applyAutoStart(enable: Boolean) {
        runCatching {
            val exePath = ProcessHandle.current().info().command().orElse(null) ?: return
            // Ignore java runner during development
            if (exePath.endsWith("java.exe", ignoreCase = true) || exePath.endsWith("javaw.exe", ignoreCase = true)) {
                return
            }
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
        }
    }
}
