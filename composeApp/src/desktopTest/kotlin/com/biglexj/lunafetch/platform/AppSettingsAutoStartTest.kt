package com.biglexj.lunafetch.platform

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsAutoStartTest {
    private fun withSettings(enabled: Boolean = true, executable: String = "C:\\Program Files\\LunaFetch\\LunaFetch.exe",
                             block: (AppSettings, List<List<String>>) -> Unit) {
        val prefs = Preferences.userRoot().node("com/biglexj/lunafetch/tests/${UUID.randomUUID()}")
        val commands = mutableListOf<List<String>>()
        try {
            prefs.putBoolean("autoStart", enabled)
            val settings = AppSettings(prefs, "Windows 11", { executable }, { commands.add(it.toList()) })
            block(settings, commands)
        } finally {
            prefs.removeNode()
        }
    }

    @Test
    fun repairsWindowsEntryDuringInitializationWithQuotedExecutable() = withSettings { _, commands ->
        assertEquals(1, commands.size)
        assertEquals(listOf("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", "LunaFetch", "/t", "REG_SZ", "/d",
            "\"C:\\Program Files\\LunaFetch\\LunaFetch.exe\" --autostart", "/f"), commands.single())
    }

    @Test
    fun doesNotEnableStartupWhenPreferenceIsDisabled() = withSettings(enabled = false) { _, commands ->
        assertTrue(commands.isEmpty())
    }

    @Test
    fun disablingRemovesWindowsEntry() = withSettings { settings, commands ->
        settings.autoStart = false
        assertEquals("delete", commands.last()[1])
        assertEquals(false, settings.autoStart)
    }

    @Test
    fun developmentDoesNotReplaceInstalledEntryWithJava() = withSettings(executable = "C:\\jdk\\bin\\java.exe") { _, commands ->
        assertTrue(commands.isEmpty())
    }
}
