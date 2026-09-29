package com.biglexj.lunafetch.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.biglexj.lunafetch.domain.NetscapeCookieJar
import com.biglexj.lunafetch.domain.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

object AndroidWebExtractor {
    private const val TAG = "LunaFetchWebExtractor"
    private const val BRIDGE_NAME = "LunaFetchBridge"

    private object BootstrapDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lower = hostname.lowercase()
            return when {
                lower == "dns.google" -> listOf(
                    InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 8, 8)),
                    InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 4, 4)),
                )
                lower == "cloudflare-dns.com" -> listOf(
                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 1, 1, 1)),
                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 0, 0, 1)),
                )
                else -> {
                    runCatching { Dns.SYSTEM.lookup(hostname) }.getOrElse {
                        if (lower.contains("pornhub.com")) {
                            listOf(InetAddress.getByAddress(hostname, byteArrayOf(66.toByte(), 254.toByte(), 114.toByte(), 41.toByte())))
                        } else throw it
                    }
                }
            }
        }
    }

    private val dohClient by lazy {
        OkHttpClient.Builder()
            .dns(BootstrapDns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resilient DNS Resolver with multi-tier fallback:
     * 1. Google DNS-over-HTTPS (via hardcoded 8.8.8.8)
     * 2. Cloudflare DNS-over-HTTPS (via hardcoded 1.1.1.1)
     * 3. Standard System DNS
     * 4. Hardcoded origin IP fallback for target domains
     */
    private object ResilientDns : Dns {
        private const val DNS_TAG = "LunaFetchResilientDns"
        private val cache = ConcurrentHashMap<String, List<InetAddress>>()

        override fun lookup(hostname: String): List<InetAddress> {
            cache[hostname]?.let { return it }

            // 1. Google DNS-over-HTTPS by hostname (resolved via 8.8.8.8 without system DNS)
            try {
                val req = Request.Builder()
                    .url("https://dns.google/resolve?name=$hostname&type=A")
                    .header("Accept", "application/json")
                    .build()
                dohClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = resp.body.string()
                        val root = Json.parseToJsonElement(json).jsonObject
                        val answers = root["Answer"]?.jsonArray
                        val ips = answers?.mapNotNull { ans ->
                            ans.jsonObject["data"]?.jsonPrimitive?.contentOrNull
                        }?.filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
                        if (!ips.isNullOrEmpty()) {
                            val list = ips.map { InetAddress.getByName(it) }
                            cache[hostname] = list
                            Log.i(DNS_TAG, "Resolved $hostname via Google DoH: $ips")
                            return list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(DNS_TAG, "Google DoH lookup failed for $hostname: ${e.message}")
            }

            // 2. Cloudflare DNS-over-HTTPS by hostname (resolved via 1.1.1.1 without system DNS)
            try {
                val req = Request.Builder()
                    .url("https://cloudflare-dns.com/dns-query?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .build()
                dohClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = resp.body.string()
                        val root = Json.parseToJsonElement(json).jsonObject
                        val answers = root["Answer"]?.jsonArray
                        val ips = answers?.mapNotNull { ans ->
                            ans.jsonObject["data"]?.jsonPrimitive?.contentOrNull
                        }?.filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }
                        if (!ips.isNullOrEmpty()) {
                            val list = ips.map { InetAddress.getByName(it) }
                            cache[hostname] = list
                            Log.i(DNS_TAG, "Resolved $hostname via Cloudflare DoH: $ips")
                            return list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(DNS_TAG, "Cloudflare DoH lookup failed for $hostname: ${e.message}")
            }

            // 3. Try standard system DNS
            try {
                val systemAddresses = Dns.SYSTEM.lookup(hostname)
                if (systemAddresses.isNotEmpty()) {
                    cache[hostname] = systemAddresses
                    return systemAddresses
                }
            } catch (e: Exception) {
                Log.w(DNS_TAG, "System DNS lookup failed for $hostname: ${e.message}")
            }

            // 4. Hardcoded origin IP fallback for known media hosts
            if (hostname.contains("pornhub.com", ignoreCase = true)) {
                val fallback = listOf(InetAddress.getByAddress(hostname, byteArrayOf(66.toByte(), 254.toByte(), 114.toByte(), 41.toByte())))
                cache[hostname] = fallback
                Log.i(DNS_TAG, "Resolved $hostname via verified origin IP: 66.254.114.41")
                return fallback
            }

            throw UnknownHostException("Unable to resolve host: $hostname")
        }
    }

    private fun getHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(ResilientDns)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getCachedInfoFile(context: Context, url: String): File {
        val canonical = canonicalUrl(url)
        val key = if (canonical.contains("viewkey=", ignoreCase = true)) {
            "viewkey_" + canonical.substringAfter("viewkey=").substringBefore("&").substringBefore("#")
        } else {
            runCatching {
                val md = MessageDigest.getInstance("MD5")
                md.digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
            }.getOrDefault(canonical.hashCode().toString())
        }
        return File(context.cacheDir, "lunafetch_info_$key.json")
    }

    fun canonicalUrl(url: String): String {
        return runCatching {
            val normalized = if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                "https://$url"
            } else url
            val uri = URI(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.contains("pornhub") && host != "www.pornhub.com") {
                val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                "https://www.pornhub.com${uri.rawPath ?: ""}$query"
            } else if (host.contains("thumbzilla") && host != "www.thumbzilla.com") {
                val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                "https://www.thumbzilla.com${uri.rawPath ?: ""}$query"
            } else {
                normalized
            }
        }.getOrDefault(url)
    }

    fun isExtractorSupported(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("pornhub.") || lower.contains("thumbzilla.")
    }

    fun invalidateCache(context: Context, url: String) {
        val file = getCachedInfoFile(context, url)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Invalidated cached info.json for $url")
        }
    }

    suspend fun extract(context: Context, url: String): VideoInfo? {
        val cached = getCachedInfoFile(context, url)
        if (cached.exists() && cached.length() > 0L && System.currentTimeMillis() - cached.lastModified() < 3600_000L) {
            val text = runCatching { cached.readText() }.getOrDefault("")
            if (text.contains("hv-h.phncdn.com") || text.contains("&f=1")) {
                Log.i(TAG, "Purged obsolete Cloudflare-gated cached info.json for $url")
                cached.delete()
            } else {
                runCatching {
                    val parsed = parseInfoJsonToVideoInfo(url, text)
                    if (parsed != null) {
                        Log.i(TAG, "Reusing valid cached info.json for $url: ${parsed.title}")
                        return parsed
                    }
                }
            }
        }

        val canonical = canonicalUrl(url)

        // Tier 1: High-Speed Direct HTTP Extraction with Resilient DoH / DNS (Runs on IO)
        val fastResult = fastHttpExtract(context, canonical)
        if (fastResult != null) {
            return fastResult
        }

        // Tier 2: Headless Chromium WebView Fallback (Runs on Main)
        Log.i(TAG, "Tier 1 Fast HTTP extraction yielded no result, attempting Tier 2 WebView fallback...")
        return withContext(Dispatchers.Main) {
            extractViaWebView(context, canonical)
        }
    }

    private suspend fun fastHttpExtract(context: Context, url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val targetUrl = canonicalUrl(url)
        val targetDomain = runCatching { URI(targetUrl).host }.getOrDefault("pornhub.com")
        Log.i(TAG, "Executing fastHttpExtract for $targetUrl...")

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", AndroidCookieJar.DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
            .header("Cookie", "platform=pc; age_verified=1; accessAgeDisclaimerPH=2; accessPH=1; cookiesBanner=1; cookieConsent=3; hasVisited=1")
            .build()

        runCatching {
            val client = getHttpClient(context)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "fastHttpExtract returned HTTP ${response.code} for $targetUrl")
                    return@withContext null
                }

                val setCookies = response.headers("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                    for (sc in setCookies) {
                        AndroidCookieJar.saveCookieHeader(context, targetDomain, sc)
                    }
                }

                val html = response.body.string()
                if (html.isBlank()) {
                    Log.w(TAG, "fastHttpExtract received empty body for $targetUrl")
                    return@withContext null
                }

                val result = parseHtmlToVideoInfo(context, targetUrl, html)
                if (result != null) {
                    Log.i(TAG, "fastHttpExtract succeeded: ${result.title} (${result.maxHeight}p)")
                }
                result
            }
        }.getOrElse { ex ->
            Log.w(TAG, "fastHttpExtract threw exception: ${ex.message}")
            null
        }
    }

    private fun extractJsonBlock(source: String, startIndex: Int): String? {
        var braceCount = 0
        var started = false
        var inString = false
        var escape = false

        for (i in startIndex until source.length) {
            val c = source[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    braceCount++
                    started = true
                } else if (c == '}') {
                    braceCount--
                    if (started && braceCount == 0) {
                        return source.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    fun parseHtmlToVideoInfo(context: Context, url: String, html: String): VideoInfo? {
        return runCatching {
            val targetDomain = runCatching { URI(url).host }.getOrDefault("pornhub.com")

            val flashvarsIndex = html.indexOf("var flashvars_")
            val jsonBlock = if (flashvarsIndex != -1) {
                val braceIndex = html.indexOf('{', flashvarsIndex)
                if (braceIndex != -1) extractJsonBlock(html, braceIndex) else null
            } else {
                val altIndex = html.indexOf("playerObjList")
                if (altIndex != -1) {
                    val braceIndex = html.indexOf('{', altIndex)
                    if (braceIndex != -1) extractJsonBlock(html, braceIndex) else null
                } else null
            }

            if (jsonBlock.isNullOrBlank()) {
                Log.w(TAG, "No flashvars or player config found in HTML for $url")
                return null
            }

            val flash = Json.parseToJsonElement(jsonBlock).jsonObject
            val title = flash["video_title"]?.jsonPrimitive?.contentOrNull
                ?: Regex("""<title>(.+?)(?:&#124;|\||- Pornhub|\n|<)""").find(html)?.groupValues?.getOrNull(1)?.trim()
                ?: "Video"
            val duration = flash["video_duration"]?.jsonPrimitive?.intOrNull ?: 0
            val thumbnail = flash["image_url"]?.jsonPrimitive?.contentOrNull
                ?: Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1).orEmpty()
            val uploader = Regex("""class=["']username["'][^>]*>([^<]+)<""").find(html)?.groupValues?.getOrNull(1)?.trim()
                ?: Regex("""class=["']bolded["'][^>]*>([^<]+)<""").find(html)?.groupValues?.getOrNull(1)?.trim()
                ?: "Web Video"
            val mediaDefs = flash["mediaDefinitions"]?.jsonArray ?: JsonArray(emptyList())

            val remoteMedias = mutableListOf<JsonObject>()
            val getMediaUrl = mediaDefs.firstNotNullOfOrNull { item ->
                item.jsonObject["videoUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.contains("/video/get_media") }
            }
            if (!getMediaUrl.isNullOrBlank()) {
                runCatching {
                    val cookieFile = AndroidCookieJar.cookieFile(context)
                    val sessionCookies = if (cookieFile.exists() && cookieFile.length() > 0) {
                        NetscapeCookieJar.parse(cookieFile.readText())
                            .filter { it.domain.contains("pornhub", ignoreCase = true) }
                            .joinToString("; ") { "${it.name}=${it.value}" }
                    } else ""
                    val cookieHeader = if (sessionCookies.isNotBlank()) {
                        "platform=pc; age_verified=1; accessAgeDisclaimerPH=2; hasVisited=1; $sessionCookies"
                    } else {
                        "platform=pc; age_verified=1; accessAgeDisclaimerPH=2; hasVisited=1"
                    }
                    val req = Request.Builder()
                        .url(getMediaUrl)
                        .header("User-Agent", AndroidCookieJar.DESKTOP_USER_AGENT)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", url)
                        .header("Origin", "https://$targetDomain")
                        .header("Cookie", cookieHeader)
                        .build()
                    val client = getHttpClient(context)
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body.string()
                            val array = Json.parseToJsonElement(body).jsonArray
                            for (elem in array) {
                                runCatching { remoteMedias.add(elem.jsonObject) }
                            }
                            Log.i(TAG, "Fetched ${remoteMedias.size} direct media format definitions from /video/get_media")
                        } else {
                            Log.w(TAG, "/video/get_media request failed with HTTP ${resp.code}")
                        }
                    }
                }.onFailure { ex ->
                    Log.e(TAG, "Exception while fetching /video/get_media: ${ex.message}", ex)
                }
            }

            val payloadObj = buildJsonObject {
                put("title", title)
                put("duration", duration)
                put("thumbnail", thumbnail)
                put("uploader", uploader)
                put("mediaDefinitions", mediaDefs)
                put("remoteMedias", buildJsonArray {
                    for (rm in remoteMedias) add(rm)
                })
            }

            processExtractedJson(context, url, payloadObj.toString())
        }.getOrElse { ex ->
            Log.e(TAG, "parseHtmlToVideoInfo failed: ${ex.message}", ex)
            null
        }
    }

    private suspend fun extractViaWebView(context: Context, url: String): VideoInfo? = withContext(Dispatchers.Main) {
        val requestUrl = canonicalUrl(url)
        val targetDomain = runCatching { URI(requestUrl).host ?: requestUrl }.getOrDefault("pornhub.com")
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        cookieManager?.setAcceptCookie(true)

        val rootDomain = NetscapeCookieJar.rootDomainOf(targetDomain)
        val seedDomain = if (rootDomain.isNotBlank()) ".$rootDomain" else targetDomain
        val baseProtocol = if (requestUrl.startsWith("http://", ignoreCase = true)) "http://" else "https://"
        val baseUrl = "$baseProtocol$targetDomain"

        val seedCookies = listOf(
            "platform=pc",
            "age_verified=1",
            "accessAgeDisclaimerPH=2",
            "accessAgeDisclaimerUK=1",
            "accessPH=1",
            "cookiesBanner=1",
            "cookieConsent=1",
            "hasVisited=1",
        )
        if (cookieManager != null) {
            for (c in seedCookies) {
                cookieManager.setCookie(baseUrl, "$c; Domain=$seedDomain; Path=/")
                cookieManager.setCookie("https://www.pornhub.com", "$c; Domain=.pornhub.com; Path=/")
                cookieManager.setCookie("https://pornhub.com", "$c; Domain=.pornhub.com; Path=/")
                if (rootDomain.isNotBlank()) {
                    cookieManager.setCookie("$baseProtocol$rootDomain", "$c; Domain=$seedDomain; Path=/")
                }
            }
            cookieManager.flush()
        }

        var webView: WebView? = null
        try {
            val extractedData = withTimeoutOrNull(18000L) {
                suspendCancellableCoroutine<String?> { continuation ->
                    var isCompleted = false
                    val handler = Handler(Looper.getMainLooper())

                    val wv = WebView(context.applicationContext).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = AndroidCookieJar.DESKTOP_USER_AGENT
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                        }
                        cookieManager?.setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun deliverResult(data: String) {
                                    Log.i(TAG, "deliverResult received: ${data.take(500)}")
                                    if (!isCompleted && continuation.isActive) {
                                        isCompleted = true
                                        continuation.resume(data)
                                    }
                                }
                            },
                            BRIDGE_NAME,
                        )

                        webViewClient = object : WebViewClient() {
                            private var attemptCount = 0

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    Log.e(TAG, "WebView main-frame error: ${error?.description} (${error?.errorCode}) for ${request.url}")
                                    if (!isCompleted && continuation.isActive) {
                                        isCompleted = true
                                        continuation.resume(null)
                                    }
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                sslHandler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?,
                            ) {
                                Log.w(TAG, "WebView SSL warning: ${error?.primaryError} for ${error?.url}, proceeding...")
                                sslHandler?.proceed()
                            }

                            private fun tryExtract(view: WebView?) {
                                if (isCompleted || !continuation.isActive) return
                                attemptCount++

                                val curUrl = view?.url ?: requestUrl
                                val captured = cookieManager?.getCookie(curUrl) ?: cookieManager?.getCookie(baseUrl)
                                if (!captured.isNullOrBlank()) {
                                    AndroidCookieJar.saveCookieHeader(context, targetDomain, captured)
                                }

                                val script = """
                                    (async function() {
                                        try {
                                            if (document.title && (document.title.indexOf('no disponible') !== -1 || document.title.indexOf('not available') !== -1)) {
                                                if (window.$BRIDGE_NAME) window.$BRIDGE_NAME.deliverResult(JSON.stringify({ error: 'unavailable' }));
                                                return;
                                            }

                                            var ageBtn = document.querySelector('#age-verification-yes, #btn-age-verify, [data-role="age-accept"], .ageDisclaimer button, #cookieBanner button, .cookie-banner button');
                                            if (ageBtn) {
                                                try { ageBtn.click(); } catch(e){}
                                            }

                                            function findFlashvars() {
                                                for (var k in window) {
                                                    if (k.indexOf('flashvars_') === 0 && window[k] && typeof window[k] === 'object') {
                                                        return window[k];
                                                    }
                                                }
                                                if (window.flashvars && typeof window.flashvars === 'object') return window.flashvars;

                                                if (window.playerObjList) {
                                                    for (var pId in window.playerObjList) {
                                                        var p = window.playerObjList[pId];
                                                        if (p && p.mediaDefinitions) return p;
                                                    }
                                                }

                                                var scripts = document.getElementsByTagName('script');
                                                for (var i = 0; i < scripts.length; i++) {
                                                    var text = scripts[i].textContent || '';
                                                    var sm = text.match(/var\s+flashvars_\d+\s*=\s*({[\s\S]+?});/);
                                                    if (sm && sm[1]) {
                                                        try {
                                                            return (Function('return (' + sm[1] + ')')());
                                                        } catch(e) {}
                                                    }
                                                }
                                                return null;
                                            }

                                            var flash = findFlashvars();
                                            if (!flash && $attemptCount < 4) {
                                                return;
                                            }

                                            var ogTitle = document.querySelector('meta[property="og:title"]');
                                            var title = (flash && flash.video_title) ? flash.video_title : (ogTitle ? ogTitle.content : document.title);
                                            var duration = (flash && flash.video_duration) ? parseInt(flash.video_duration) : 0;
                                            var ogImage = document.querySelector('meta[property="og:image"]');
                                            var thumbnail = (flash && flash.image_url) ? flash.image_url : (ogImage ? ogImage.content : "");
                                            var uploader = "";
                                            var uploaderEl = document.querySelector('.username') || document.querySelector('[data-role="user-name"]') || document.querySelector('.userInfo .bolded');
                                            if (uploaderEl) uploader = uploaderEl.textContent.trim();

                                            var mediaDefs = (flash && flash.mediaDefinitions) ? flash.mediaDefinitions : [];
                                            var remoteMedias = [];

                                            var mp4Def = mediaDefs.find(function(m) { return m && m.videoUrl && m.videoUrl.indexOf('/video/get_media') !== -1; });
                                            if (mp4Def && mp4Def.videoUrl) {
                                                try {
                                                    var mediaReqUrl = mp4Def.videoUrl;
                                                    var resp = await fetch(mediaReqUrl, {
                                                        credentials: 'include',
                                                        headers: { 'X-Requested-With': 'XMLHttpRequest' }
                                                    });
                                                    remoteMedias = await resp.json();
                                                } catch(e) {}
                                            }

                                            var payload = {
                                                title: title,
                                                duration: duration,
                                                thumbnail: thumbnail,
                                                uploader: uploader,
                                                mediaDefinitions: mediaDefs,
                                                remoteMedias: Array.isArray(remoteMedias) ? remoteMedias : []
                                            };

                                            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.deliverResult) {
                                                window.$BRIDGE_NAME.deliverResult(JSON.stringify(payload));
                                            }
                                        } catch(err) {
                                            if ($attemptCount >= 4 && window.$BRIDGE_NAME && window.$BRIDGE_NAME.deliverResult) {
                                                window.$BRIDGE_NAME.deliverResult(JSON.stringify({ error: err.toString(), title: document.title }));
                                            }
                                        }
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(script, null)

                                if (!isCompleted && attemptCount < 4) {
                                    handler.postDelayed({ tryExtract(view) }, 1000)
                                }
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                Log.i(TAG, "onPageFinished: $finishedUrl, pageTitle='${view?.title}'")
                                handler.postDelayed({ tryExtract(view) }, 800)
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
                    Log.i(TAG, "Loading URL in WebView: $requestUrl")
                    wv.loadUrl(requestUrl)
                }
            }

            if (!extractedData.isNullOrBlank()) {
                val parsed = processExtractedJson(context, url, extractedData)
                if (parsed != null) {
                    Log.i(TAG, "Successfully extracted video metadata via WebView: ${parsed.title} (${parsed.maxHeight}p)")
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebView extraction failed for $url: ${e.message}")
        } finally {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
        null
    }

    private fun processExtractedJson(context: Context, url: String, rawJson: String): VideoInfo? {
        return runCatching {
            val root = Json.parseToJsonElement(rawJson).jsonObject
            val title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Video" }
            if (title == "Página web no disponible" || title.contains("Webpage not available", ignoreCase = true)) {
                return null
            }
            val duration = root["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val thumbnail = root["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val uploader = root["uploader"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Web Video" }

            val rawRemote = root["remoteMedias"]?.jsonArray ?: JsonArray(emptyList())
            val rawMediaDefs = root["mediaDefinitions"]?.jsonArray ?: JsonArray(emptyList())

            data class ExtractedFormat(
                val url: String,
                val height: Int,
                val formatId: String,
                val isHls: Boolean,
                val bitrate: Int? = null,
            )

            val formats = mutableListOf<ExtractedFormat>()

            fun parseHeight(rawQuality: JsonElement?, fallbackUrl: String): Int {
                val qStr = when (rawQuality) {
                    is JsonPrimitive -> rawQuality.contentOrNull
                    is JsonArray -> rawQuality.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                    else -> null
                }
                val fromQuality = qStr?.filter { it.isDigit() }?.toIntOrNull()
                if (fromQuality != null && fromQuality > 0) return fromQuality
                val match = Regex("""(?i)(\d{3,4})[pP]""").find(fallbackUrl)
                return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 720
            }

            // 1. Process remoteMedias (direct CDN endpoints from /video/get_media)
            for (item in rawRemote) {
                val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
                val videoUrl = obj["videoUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                if (videoUrl.isBlank() || !videoUrl.startsWith("http")) continue

                val formatStr = obj["format"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                val isHls = formatStr.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8", ignoreCase = true)

                val height = parseHeight(obj["quality"], videoUrl)
                val formatId = if (isHls) "hls-${height}p" else "http-${height}p"
                formats.add(ExtractedFormat(url = videoUrl, height = height, formatId = formatId, isHls = isHls))
            }

            // 2. Process mediaDefinitions from flashvars (contains live master.m3u8 CDN streams)
            for (item in rawMediaDefs) {
                val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
                val videoUrl = obj["videoUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                if (videoUrl.isBlank() || !videoUrl.startsWith("http") || videoUrl.contains("/video/get_media")) continue

                val formatStr = obj["format"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                val isHls = formatStr.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8", ignoreCase = true)

                val height = parseHeight(obj["quality"] ?: obj["height"], videoUrl)
                val bitrate = Regex("""(\d+)K""").find(videoUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val formatId = if (isHls) "hls-${height}p" else "http-${height}p"
                formats.add(ExtractedFormat(url = videoUrl, height = height, formatId = formatId, isHls = isHls, bitrate = bitrate))
            }

            if (formats.isEmpty()) {
                Log.w(TAG, "No video formats could be parsed from extraction payload for $url. Raw payload: ${rawJson.take(500)}")
                return null
            }

            val directMp4s = formats.filter { !it.isHls }
            val candidateFormats = if (directMp4s.isNotEmpty()) {
                Log.i(TAG, "Using ${directMp4s.size} direct MP4 streams for download (excluding HLS)")
                directMp4s
            } else {
                Log.i(TAG, "No direct MP4 streams found, falling back to ${formats.size} HLS streams")
                formats
            }

            val maxHeight = candidateFormats.maxOf { it.height }.coerceAtLeast(360)
            val domain = runCatching { URI(url).host }.getOrDefault("www.pornhub.com")

            // Build yt-dlp compatible info.json
            val infoJsonObj = buildJsonObject {
                put("id", url.substringAfterLast("viewkey=").substringBefore("&").ifBlank { "video" })
                put("title", title)
                put("uploader", uploader)
                put("duration", duration)
                put("thumbnail", thumbnail)
                put("webpage_url", url)
                put("extractor", "generic")
                put("extractor_key", "Generic")
                put("formats", buildJsonArray {
                    for (f in candidateFormats.distinctBy { it.url }) {
                        add(buildJsonObject {
                            put("url", f.url)
                            put("format_id", if (f.isHls) "hls-${f.height}p" else "${f.height}p")
                            put("height", f.height)
                            put("ext", "mp4")
                            put("protocol", if (f.isHls) "m3u8_native" else "https")
                            if (f.isHls) {
                                put("manifest_url", f.url)
                            }
                            put("vcodec", "avc1")
                            put("acodec", "mp4a")
                            f.bitrate?.let { put("tbr", it) }
                            put("http_headers", buildJsonObject {
                                put("Origin", "https://$domain")
                                put("Referer", "https://$domain/")
                                put("User-Agent", AndroidCookieJar.DESKTOP_USER_AGENT)
                            })
                        })
                    }
                })
            }

            // Save info.json to cache file for yt-dlp --load-info-json
            val cacheFile = getCachedInfoFile(context, url)
            cacheFile.writeText(infoJsonObj.toString())
            Log.i(TAG, "Saved web extraction info.json (${cacheFile.length()} bytes) to ${cacheFile.absolutePath}")

            VideoInfo(
                url = url,
                title = title,
                uploader = uploader,
                durationSeconds = duration.toDouble(),
                thumbnailUrl = thumbnail,
                maxHeight = maxHeight,
                collectionTitle = null,
                collectionCount = 0,
                collectionEntries = emptyList(),
            )
        }.getOrElse { ex ->
            Log.e(TAG, "processExtractedJson exception for $url: ${ex.message}", ex)
            null
        }
    }

    private fun parseInfoJsonToVideoInfo(url: String, jsonString: String): VideoInfo? {
        return runCatching {
            val root = Json.parseToJsonElement(jsonString).jsonObject
            val title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Video" }
            val uploader = root["uploader"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Web Video" }
            val duration = root["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val thumbnail = root["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val formats = root["formats"]?.jsonArray ?: JsonArray(emptyList())
            val maxHeight = formats.maxOfOrNull { it.jsonObject["height"]?.jsonPrimitive?.intOrNull ?: 0 }
                ?.takeIf { it > 0 } ?: 1080

            VideoInfo(
                url = url,
                title = title,
                uploader = uploader,
                durationSeconds = duration.toDouble(),
                thumbnailUrl = thumbnail,
                maxHeight = maxHeight,
                collectionTitle = null,
                collectionCount = 0,
                collectionEntries = emptyList(),
            )
        }.getOrNull()
    }
}
