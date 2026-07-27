package com.biglexj.lunafetch.core.theme

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

actual fun isPlatformInDarkTheme(): Boolean {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    if (osName.contains("win")) {
        return runCatching {
            val keyPath = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
            val valName = "AppsUseLightTheme"
            if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, keyPath) &&
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, keyPath, valName)) {
                Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, keyPath, valName) == 0
            } else {
                false
            }
        }.getOrDefault(false)
    }
    return false
}
