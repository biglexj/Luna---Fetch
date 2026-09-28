package com.biglexj.lunafetch.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.biglexj.lunafetch.domain.NetscapeCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

object AndroidCookieJar {
    private const val TAG = "LunaFetchCookieJar"
    private const val COOKIE_FILE_NAME = "luna_session_cookies.txt"
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    fun cookieFile(context: Context): File = File(context.filesDir, COOKIE_FILE_NAME)

    @Synchronized
    fun ensureClearanceCookies(context: Context, url: String): File {
        val target = cookieFile(context)
        val defaultCookies = NetscapeCookieJar.defaultClearanceForUrl(url)
        val existingCookies = if (target.exists() && target.length() > 0) {
            runCatching { NetscapeCookieJar.parse(target.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val merged = NetscapeCookieJar.merge(existingCookies, defaultCookies)
        if (merged.isNotEmpty()) {
            val serialized = NetscapeCookieJar.serialize(merged)
            target.writeText(serialized)
            Log.d(TAG, "Clearance cookies ensured for $url (${merged.size} total cookies in jar)")
        }
        return target
    }

    suspend fun resolveWebCookies(context: Context, url: String): Boolean = withContext(Dispatchers.Main) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return@withContext false
        cookieManager.setAcceptCookie(true)

        val targetDomain = runCatching {
            java.net.URI(url).host ?: url
        }.getOrDefault("pornhub.com")

        val rootDomain = NetscapeCookieJar.rootDomainOf(targetDomain)
        val seedDomain = if (rootDomain.isNotBlank()) ".$rootDomain" else targetDomain
        val baseProtocol = if (url.startsWith("http://", ignoreCase = true)) "http://" else "https://"
        val baseUrl = "$baseProtocol$targetDomain"

        // Pre-seed known bypass cookies across root and regional domains
        val seedUrls = listOf(
            baseUrl,
            "$baseProtocol$rootDomain",
            "$baseProtocol" + "es.$rootDomain",
            "$baseProtocol" + "www.$rootDomain",
        )
        val seedCookies = listOf(
            "platform=pc; Domain=$seedDomain; Path=/",
            "age_verified=1; Domain=$seedDomain; Path=/",
            "accessAgeDisclaimerPH=1; Domain=$seedDomain; Path=/",
            "accessAgeDisclaimerUK=1; Domain=$seedDomain; Path=/",
            "accessPH=1; Domain=$seedDomain; Path=/",
            "ua=7675d59b5e84e0a878ee6f0a97f9056f; Domain=$seedDomain; Path=/",
            "cookiesBanner=1; Domain=$seedDomain; Path=/",
            "cookieConsent=1; Domain=$seedDomain; Path=/",
        )
        for (seedUrl in seedUrls) {
            for (c in seedCookies) {
                cookieManager.setCookie(seedUrl, c)
            }
        }
        cookieManager.flush()

        var webView: WebView? = null
        try {
            val capturedCookies = withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Pair<String?, String?>?> { continuation ->
                    val wv = WebView(context.applicationContext).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = DESKTOP_USER_AGENT
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val finalUrl = finishedUrl ?: view?.url ?: url
                                    val cookies = cookieManager.getCookie(finalUrl) ?: cookieManager.getCookie(baseUrl)
                                    if (continuation.isActive) {
                                        continuation.resume(Pair(cookies, finalUrl))
                                    }
                                }, 800)
                            }
                        }
                    }
                    webView = wv
                    continuation.invokeOnCancellation {
                        runCatching {
                            wv.stopLoading()
                            wv.destroy()
                        }
                    }
                    wv.loadUrl(url)
                }
            }

            val cookies = capturedCookies?.first
            val finalUrl = capturedCookies?.second ?: url
            val finalHost = runCatching { java.net.URI(finalUrl).host }.getOrNull() ?: targetDomain

            if (!cookies.isNullOrBlank()) {
                Log.d(TAG, "Captured web cookies for $finalUrl (host=$finalHost): ${cookies.take(120)}...")
                saveCookieHeader(context, finalHost, cookies)
                return@withContext true
            } else {
                val fallbackCookies = cookieManager.getCookie(baseUrl) ?: cookieManager.getCookie(url)
                if (!fallbackCookies.isNullOrBlank()) {
                    Log.d(TAG, "Using CookieManager fallback cookies for $baseUrl: ${fallbackCookies.take(120)}...")
                    saveCookieHeader(context, targetDomain, fallbackCookies)
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve cookies via WebView: ${e.message}")
        } finally {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
        false
    }

    @Synchronized
    fun saveCookieHeader(context: Context, domain: String, cookieHeader: String) {
        val target = cookieFile(context)
        val newCookies = NetscapeCookieJar.parseHeaderString(domain, cookieHeader)
        if (newCookies.isEmpty()) return

        val existing = if (target.exists() && target.length() > 0) {
            runCatching { NetscapeCookieJar.parse(target.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val merged = NetscapeCookieJar.merge(existing, newCookies)
        target.writeText(NetscapeCookieJar.serialize(merged))
        Log.d(TAG, "Saved ${newCookies.size} cookies for $domain into jar (Total: ${merged.size})")
    }

    @Synchronized
    fun importCookies(context: Context, rawText: String): Int {
        val target = cookieFile(context)
        val parsed = if (rawText.contains("\t")) {
            NetscapeCookieJar.parse(rawText)
        } else {
            NetscapeCookieJar.parseHeaderString("unknown", rawText)
        }
        if (parsed.isEmpty()) return 0

        val existing = if (target.exists() && target.length() > 0) {
            runCatching { NetscapeCookieJar.parse(target.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val merged = NetscapeCookieJar.merge(existing, parsed)
        target.writeText(NetscapeCookieJar.serialize(merged))
        return parsed.size
    }
}
