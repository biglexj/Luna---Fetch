package com.biglexj.lunafetch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.biglexj.lunafetch.feature.LunaFetchApp
import com.biglexj.lunafetch.platform.AndroidPlatformBindings

class QuickDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUrl = intent.sharedUrl() ?: run {
            finish()
            return
        }
        setContent {
            val bindings = remember { AndroidPlatformBindings(this) { null } }
            LunaFetchApp(
                platform = bindings,
                quickDownloadUrl = sharedUrl,
                onDismissQuickDownload = ::finish,
            )
        }
    }

    private fun Intent.sharedUrl(): String? {
        if (action != Intent.ACTION_SEND || type != "text/plain") return null
        return UrlPattern.find(getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value
    }

    private companion object {
        val UrlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
