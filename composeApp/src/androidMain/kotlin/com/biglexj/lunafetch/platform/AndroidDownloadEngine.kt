package com.biglexj.lunafetch.platform

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.CookieManager
import com.biglexj.lunafetch.domain.DownloadEngine
import com.biglexj.lunafetch.domain.DownloadException
import com.biglexj.lunafetch.domain.DownloadProgress
import com.biglexj.lunafetch.domain.DownloadRequest
import com.biglexj.lunafetch.domain.DownloadResult
import com.biglexj.lunafetch.domain.CollectionEntry
import com.biglexj.lunafetch.domain.VideoInfo
import com.biglexj.lunafetch.domain.YtdlpProtocol
import com.biglexj.lunafetch.domain.isCollection
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class AndroidDownloadEngine(private val context: Context) : DownloadEngine {
    @Volatile
    private var processId: String? = null
    @Volatile
    private var initialized = false

    override suspend fun analyze(url: String): VideoInfo = withContext(Dispatchers.IO) {
        initialize()
        val canonicalUrl = AndroidWebExtractor.canonicalUrl(url)
        val isImpersonateDomain = AndroidWebExtractor.isExtractorSupported(canonicalUrl)

        if (isImpersonateDomain) {
            android.util.Log.i("LunaFetchEngine", "Domain requires TLS impersonation. Prioritizing AndroidWebExtractor for $canonicalUrl...")
            val webExtract = runCatching { AndroidWebExtractor.extract(context, canonicalUrl) }.getOrNull()
            if (webExtract != null) {
                android.util.Log.i("LunaFetchEngine", "Primary analyze succeeded via AndroidWebExtractor: ${webExtract.title}")
                return@withContext webExtract
            }
            android.util.Log.w("LunaFetchEngine", "Primary AndroidWebExtractor returned null, falling back to standard pipeline...")
        }

        try {
            // First attempt: clean analyze without cookies or conflicting headers (Seal parity)
            executeAnalyze(url, useCookies = false)
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            val ver = runCatching { YoutubeDL.version(context) }.getOrNull().orEmpty()
            android.util.Log.e("LunaFetchEngine", "Clean analyze failed: $message (yt-dlp version: $ver). Running fallbacks...")
            if (message.contains("403", ignoreCase = true) ||
                message.contains("Forbidden", ignoreCase = true) ||
                message.contains("impersonat", ignoreCase = true) ||
                message.contains("Unable to download webpage", ignoreCase = true) ||
                message.contains("Unable to extract", ignoreCase = true) ||
                message.contains("Unexpected response", ignoreCase = true) ||
                message.contains("Sign in", ignoreCase = true) ||
                message.contains("bot", ignoreCase = true) ||
                message.contains("please report this issue", ignoreCase = true)
            ) {
                // Strategy 0: Direct Web Extraction via Android Web Extractor (bypasses Cloudflare TLS impersonation block)
                android.util.Log.i("LunaFetchEngine", "Attempting AndroidWebExtractor for $canonicalUrl...")
                val webExtract = runCatching { AndroidWebExtractor.extract(context, canonicalUrl) }.getOrNull()
                if (webExtract != null) {
                    android.util.Log.i("LunaFetchEngine", "Analyze succeeded via AndroidWebExtractor: ${webExtract.title}")
                    return@withContext webExtract
                }

                // Strategy 1: Resolve live session cookies via headless WebView + domain clearance
                android.util.Log.i("LunaFetchEngine", "Resolving live session cookies via WebView for $url...")
                runCatching { AndroidCookieJar.resolveWebCookies(context, url) }
                AndroidCookieJar.ensureClearanceCookies(context, url)
                val retryCookies = runCatching {
                    executeAnalyze(
                        url = url,
                        useCookies = true,
                        customUserAgent = AndroidCookieJar.DESKTOP_USER_AGENT,
                    )
                }
                if (retryCookies.isSuccess) {
                    android.util.Log.i("LunaFetchEngine", "Analyze succeeded via WebView cookies fallback!")
                    return@withContext retryCookies.getOrThrow()
                } else {
                    android.util.Log.w("LunaFetchEngine", "Analyze with WebView cookies failed: ${retryCookies.exceptionOrNull()?.message}")
                }

                // Strategy 2: Update yt-dlp to latest NIGHTLY immediately (addresses web changes like 403)
                android.util.Log.e("LunaFetchEngine", "Triggering forced update to NIGHTLY and retrying...")
                val updateRes = runCatching { updateYtdlpIfNeeded(forceNightly = true) }
                if (updateRes.isSuccess) {
                    val retryNightlyCookies = runCatching {
                        executeAnalyze(
                            url = url,
                            useCookies = true,
                            customUserAgent = AndroidCookieJar.DESKTOP_USER_AGENT,
                        )
                    }
                    if (retryNightlyCookies.isSuccess) {
                        return@withContext retryNightlyCookies.getOrThrow()
                    }
                    val retryNightlyClean = runCatching { executeAnalyze(url, useCookies = false) }
                    if (retryNightlyClean.isSuccess) {
                        return@withContext retryNightlyClean.getOrThrow()
                    }
                }

                // Strategy 3: Retry with alternative mobile user-agent
                android.util.Log.e("LunaFetchEngine", "Retrying analyze with mobile user-agent...")
                val mobileUa = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
                val retryMobile = runCatching { executeAnalyze(url, useCookies = false, customUserAgent = mobileUa) }
                if (retryMobile.isSuccess) {
                    return@withContext retryMobile.getOrThrow()
                }

                throw DownloadException(error.message ?: "No se pudo analizar el enlace en Android.", error)
            } else {
                throw DownloadException(error.message ?: "No se pudo analizar el enlace en Android.", error)
            }
        }
    }

    private fun executeAnalyze(
        url: String,
        useCookies: Boolean = false,
        customUserAgent: String? = null,
        verbose: Boolean = false,
    ): VideoInfo {
        val isPlaylist = url.contains("list=", ignoreCase = true) ||
            url.contains("/playlist", ignoreCase = true) ||
            url.contains("/sets/", ignoreCase = true)

        val cookieFile = if (useCookies) {
            AndroidCookieJar.cookieFile(context).takeIf { it.exists() && it.length() > 0 }
                ?: syncWebViewCookies()
                ?: AndroidCookieJar.ensureClearanceCookies(context, url).takeIf { it.exists() && it.length() > 0 }
        } else null

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "%(title).200B")
            addOption("--dump-single-json")
            if (isPlaylist) {
                addOption("--flat-playlist")
            } else {
                addOption("--no-playlist")
            }
            addOption("-R", "1")
            addOption("-4") // Force IPv4 to bypass mobile carrier IPv6 403 Forbidden
            addOption("--socket-timeout", "10")
            if (verbose) {
                addOption("-v")
            }
            if (!customUserAgent.isNullOrBlank()) {
                addOption("--user-agent", customUserAgent)
            }
            if (cookieFile != null && cookieFile.exists() && cookieFile.length() > 0) {
                addOption("--cookies", cookieFile.absolutePath)
            }
        }
        android.util.Log.e("LunaFetchEngine", "executeAnalyze url: $url, useCookies: ${cookieFile != null}, isPlaylist: $isPlaylist, ua: ${customUserAgent ?: "native"}")
        val response = try {
            YoutubeDL.execute(request)
        } catch (e: Exception) {
            android.util.Log.e("LunaFetchEngine", "YoutubeDL.execute failed for $url (useCookies=${cookieFile != null}): ${e.message}")
            throw e
        }
        android.util.Log.e("LunaFetchEngine", "executeAnalyze exitCode: ${response.exitCode}, err: ${response.err.take(500)}")
        if (response.exitCode != 0) {
            throw DownloadException(response.err.ifBlank { "yt-dlp no pudo analizar el enlace." })
        }
        val parsed = response.out.toVideoInfo(url)
        return if (parsed.thumbnailUrl.isBlank() && parsed.isCollection) {
            response.out.firstCollectionVideoUrl()?.let { firstVideoUrl ->
                val firstItem = YoutubeDL.getInfo(
                    androidRequest(firstVideoUrl, useCookies).addOption("--no-playlist"),
                )
                parsed.copy(
                    thumbnailUrl = firstItem.thumbnail.orEmpty().ifBlank {
                        firstItem.thumbnails?.lastOrNull()?.url.orEmpty()
                    },
                )
            } ?: parsed
        } else {
            parsed
        }
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        initialize()
        if (request.destination.isBlank()) {
            throw DownloadException("No se ha seleccionado una carpeta de destino en este dispositivo.")
        }
        val treeUri = runCatching { Uri.parse(request.destination) }.getOrNull()
            ?.takeIf { it != Uri.EMPTY && !it.scheme.isNullOrBlank() }
            ?: throw DownloadException("Selecciona una carpeta de destino válida con permisos de almacenamiento.")
        val workRoot = File(context.cacheDir, "downloads")
        val workDirectory = File(workRoot, System.currentTimeMillis().toString()).apply { mkdirs() }
        val outputTemplate = File(
            workDirectory,
            if (request.downloadCollection) "%(playlist_index)03d - %(title)s.%(ext)s" else "%(title)s.%(ext)s",
        ).absolutePath
        val id = "lunafetch-${System.currentTimeMillis()}"
        processId = id
        DownloadForegroundService.start(context)

        try {
            val command = YtdlpProtocol.buildDownloadArguments(request, outputTemplate)
            val canonicalUrl = AndroidWebExtractor.canonicalUrl(request.url)
            val isImpersonateDomain = AndroidWebExtractor.isExtractorSupported(canonicalUrl)
            val cachedInfo = AndroidWebExtractor.getCachedInfoFile(context, canonicalUrl)
            val hasCachedInfo = cachedInfo.exists() && cachedInfo.length() > 0
            val infoJsonFile = if (hasCachedInfo) {
                cachedInfo
            } else if (isImpersonateDomain) {
                android.util.Log.i("LunaFetchEngine", "Pre-extracting info JSON for impersonated domain download: $canonicalUrl")
                runCatching { AndroidWebExtractor.extract(context, canonicalUrl) }.getOrNull()
                AndroidWebExtractor.getCachedInfoFile(context, canonicalUrl).takeIf { it.exists() && it.length() > 0 }
            } else null

            if (isImpersonateDomain && infoJsonFile == null) {
                throw DownloadException("No se pudo obtener el flujo de descarga para este enlace. Por favor verifica tu conexión o intenta nuevamente.")
            }

            val youtubeRequest = if (infoJsonFile != null) {
                android.util.Log.i("LunaFetchEngine", "Executing download with pre-extracted info JSON: ${infoJsonFile.absolutePath}")
                YoutubeDLRequest(emptyList()).apply {
                    addOption("-4")
                    addOption("--socket-timeout", "15")
                    addOption("--user-agent", AndroidCookieJar.DESKTOP_USER_AGENT)
                    addOption("--load-info-json", infoJsonFile.absolutePath)
                    val cookieFile = AndroidCookieJar.cookieFile(context).takeIf { it.exists() && it.length() > 0 }
                    cookieFile?.let { addOption("--cookies", it.absolutePath) }
                    addCommands(command)
                }
            } else {
                androidRequest(request.url, useCookies = true).addCommands(command)
            }
            val response = try {
                YoutubeDL.execute(youtubeRequest, id) { percentage, etaSeconds, line ->
                    onLog(line)
                    YtdlpProtocol.parseProgress(line)?.let(onProgress)
                    val progress = DownloadProgress(
                        percentage = percentage.toDouble().coerceIn(0.0, 100.0),
                        eta = etaSeconds.takeIf { it >= 0 }?.let { "${it}s" }.orEmpty(),
                    )
                    onProgress(progress)
                    DownloadForegroundService.update(context, percentage.toInt())
                }
            } catch (downloadErr: Exception) {
                val msg = downloadErr.message.orEmpty()
                if (msg.contains("403", ignoreCase = true) ||
                    msg.contains("410", ignoreCase = true) ||
                    msg.contains("Gone", ignoreCase = true) ||
                    msg.contains("Forbidden", ignoreCase = true) ||
                    msg.contains("impersonat", ignoreCase = true) ||
                    msg.contains("Unable to download webpage", ignoreCase = true)
                ) {
                    android.util.Log.w("LunaFetchEngine", "Download hit $msg. Invalidating cache and re-extracting live info via AndroidWebExtractor...")
                    AndroidWebExtractor.invalidateCache(context, canonicalUrl)
                    val webExtract = runCatching { AndroidWebExtractor.extract(context, canonicalUrl) }.getOrNull()
                    val freshCached = AndroidWebExtractor.getCachedInfoFile(context, canonicalUrl)
                    if (webExtract != null && freshCached.exists() && freshCached.length() > 0) {
                        val jsonRequest = YoutubeDLRequest(emptyList()).apply {
                            addOption("-4")
                            addOption("--socket-timeout", "15")
                            addOption("--user-agent", AndroidCookieJar.DESKTOP_USER_AGENT)
                            addOption("--load-info-json", freshCached.absolutePath)
                            val cookieFile = AndroidCookieJar.cookieFile(context).takeIf { it.exists() && it.length() > 0 }
                            cookieFile?.let { addOption("--cookies", it.absolutePath) }
                            addCommands(command)
                        }
                        YoutubeDL.execute(jsonRequest, id) { percentage, etaSeconds, line ->
                            onLog(line)
                            YtdlpProtocol.parseProgress(line)?.let(onProgress)
                            val progress = DownloadProgress(
                                percentage = percentage.toDouble().coerceIn(0.0, 100.0),
                                eta = etaSeconds.takeIf { it >= 0 }?.let { "${it}s" }.orEmpty(),
                            )
                            onProgress(progress)
                            DownloadForegroundService.update(context, percentage.toInt())
                        }
                    } else {
                        android.util.Log.w("LunaFetchEngine", "Download fallback to unauthenticated request...")
                        val unauthRequest = androidRequest(request.url, useCookies = false).addCommands(command)
                        YoutubeDL.execute(unauthRequest, id) { percentage, etaSeconds, line ->
                            onLog(line)
                            YtdlpProtocol.parseProgress(line)?.let(onProgress)
                            val progress = DownloadProgress(
                                percentage = percentage.toDouble().coerceIn(0.0, 100.0),
                                eta = etaSeconds.takeIf { it >= 0 }?.let { "${it}s" }.orEmpty(),
                            )
                            onProgress(progress)
                            DownloadForegroundService.update(context, percentage.toInt())
                        }
                    }
                } else {
                    throw downloadErr
                }
            }
            if (response.exitCode != 0) {
                throw DownloadException(response.err.ifBlank { "yt-dlp terminó con código ${response.exitCode}." })
            }
            response.out.lineSequence().filter(String::isNotBlank).forEach(onLog)
            fun isValidMediaFile(file: File): Boolean {
                if (!file.isFile || file.length() == 0L) return false
                if (file.extension.equals("part", true) || file.extension.equals("ytdl", true)) return false
                if (file.length() < 64 * 1024L) {
                    val header = runCatching {
                        file.inputStream().use { stream ->
                            val buf = ByteArray(64)
                            val read = stream.read(buf)
                            if (read > 0) String(buf, 0, read) else ""
                        }
                    }.getOrDefault("")
                    if (header.contains("#EXTM3U", ignoreCase = true) ||
                        header.contains("<html", ignoreCase = true) ||
                        header.contains("<!DOCTYPE", ignoreCase = true)
                    ) {
                        android.util.Log.e("LunaFetchEngine", "Rejected corrupt/manifest file saved as media: ${file.name} (${file.length()} bytes)")
                        return false
                    }
                }
                return true
            }

            val downloaded = response.out.lineSequence()
                .mapNotNull(YtdlpProtocol::outputPath)
                .map(::File)
                .filter(::isValidMediaFile)
                .distinctBy { it.absolutePath }
                .toList()
                .ifEmpty {
                    workDirectory.walkTopDown()
                        .filter(::isValidMediaFile)
                        .toList()
                }
            if (downloaded.isEmpty()) {
                throw DownloadException("La descarga no produjo un archivo de video válido. Por favor verifica el enlace e intenta nuevamente.")
            }
            val resultUris = copyToTree(downloaded, treeUri)
            DownloadResult(
                outputPaths = resultUris.map(Uri::toString),
                openPath = if (resultUris.size == 1) resultUris.first().toString() else treeUri.toString(),
            )
        } catch (error: Exception) {
            throw DownloadException(error.message ?: "No se pudo completar la descarga en Android.", error)
        } finally {
            processId = null
            DownloadForegroundService.stop(context)
            workDirectory.deleteRecursively()
        }
    }

    override fun cancel() {
        processId?.let(YoutubeDL::destroyProcessById)
        processId = null
        DownloadForegroundService.stop(context)
    }

    @Synchronized
    private fun initialize() {
        if (initialized) return
        try {
            YoutubeDL.init(context)
            FFmpeg.init(context)
            syncWebViewCookies()
            updateYtdlpIfNeeded()
            initialized = true
        } catch (error: Exception) {
            throw DownloadException("No se pudo inicializar el motor local de Android.", error)
        }
    }

    private fun syncWebViewCookies(): File? {
        return runCatching {
            CookieManager.getInstance().flush()
            val dbFile = File(context.applicationInfo.dataDir, "app_webview/Default/Cookies")
            val cookieList = mutableListOf<String>()

            if (dbFile.exists() && dbFile.length() > 0L) {
                runCatching {
                    val db = SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    )
                    val cursor = db.query(
                        "cookies",
                        arrayOf("host_key", "name", "value", "path", "is_secure", "expires_utc"),
                        null, null, null, null, null,
                    )
                    cursor.use { c ->
                        val hostIdx = c.getColumnIndexOrThrow("host_key")
                        val nameIdx = c.getColumnIndexOrThrow("name")
                        val valIdx = c.getColumnIndexOrThrow("value")
                        val pathIdx = c.getColumnIndexOrThrow("path")
                        val secIdx = c.getColumnIndexOrThrow("is_secure")
                        val expIdx = c.getColumnIndexOrThrow("expires_utc")
                        while (c.moveToNext()) {
                            val hostKey = c.getString(hostIdx).orEmpty()
                            val name = c.getString(nameIdx).orEmpty()
                            val value = c.getString(valIdx).orEmpty()
                            val path = c.getString(pathIdx).orEmpty().ifBlank { "/" }
                            val isSecure = if (c.getInt(secIdx) == 1) "TRUE" else "FALSE"
                            val expiryRaw = c.getLong(expIdx)
                            val expiration = (expiryRaw / 1000000L - 11644473600L).coerceAtLeast(0L)
                            val host = if (!hostKey.startsWith(".")) ".$hostKey" else hostKey
                            val includeSubdomains = if (host.startsWith(".")) "TRUE" else "FALSE"
                            if (name.isNotBlank() && value.isNotBlank()) {
                                cookieList.add("$host\t$includeSubdomains\t$path\t$isSecure\t$expiration\t$name\t$value")
                            }
                        }
                    }
                    db.close()
                }.onFailure {
                    android.util.Log.w("LunaFetchEngine", "Could not read SQLite cookies: ${it.message}")
                }
            }

            if (cookieList.isNotEmpty()) {
                val targetFile = File(context.cacheDir, "luna_session_cookies.txt")
                val content = "# Netscape HTTP Cookie File\n" + cookieList.distinct().joinToString("\n") + "\n"
                targetFile.writeText(content)
                android.util.Log.i("LunaFetchEngine", "Synchronized ${cookieList.size} cookies to ${targetFile.absolutePath}")
                targetFile
            } else {
                null
            }
        }.getOrNull()
    }

    private fun androidRequest(url: String, useCookies: Boolean = true): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        req.addOption("-4") // Force IPv4
        req.addOption("--socket-timeout", "10")
        // Use Android mobile User-Agent so CDN routes to Akamai (km-h.phncdn.com) instead of Cloudflare
        req.addOption("--user-agent", AndroidCookieJar.ANDROID_USER_AGENT)
        if (useCookies) {
            val cookieFile = AndroidCookieJar.cookieFile(context).takeIf { it.exists() && it.length() > 0 }
                ?: syncWebViewCookies()
                ?: AndroidCookieJar.ensureClearanceCookies(context, url).takeIf { it.exists() && it.length() > 0 }
            cookieFile?.let { req.addOption("--cookies", it.absolutePath) }
        }
        return req
    }

    private fun updateYtdlpIfNeeded(forceNightly: Boolean = false) {
        val preferences = context.getSharedPreferences("lunafetch", Context.MODE_PRIVATE)
        val lastUpdate = preferences.getLong("lastYtdlpUpdate", 0L)
        val lastChannel = preferences.getString("lastYtdlpChannel", "NIGHTLY") ?: "NIGHTLY"
        val now = System.currentTimeMillis()
        val currentVer = runCatching { YoutubeDL.version(context) }.getOrNull().orEmpty()
        val isOutdated = currentVer.isBlank() || !currentVer.startsWith("2026")

        android.util.Log.d("LunaFetchEngine", "updateYtdlpIfNeeded: forceNightly=$forceNightly, lastChannel=$lastChannel, currentVersion=$currentVer, isOutdated=$isOutdated")
        if (!forceNightly && !isOutdated && lastChannel == "NIGHTLY" && now - lastUpdate < UpdateIntervalMillis) {
            android.util.Log.d("LunaFetchEngine", "updateYtdlpIfNeeded: Skipped because updated recently (${now - lastUpdate}ms ago)")
            return
        }

        val nightlyResult = runCatching {
            android.util.Log.d("LunaFetchEngine", "Updating to NIGHTLY channel...")
            val res = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
            android.util.Log.d("LunaFetchEngine", "Update to NIGHTLY status: $res, version is now: ${YoutubeDL.version(context)}")
            preferences.edit().putLong("lastYtdlpUpdate", now).putString("lastYtdlpChannel", "NIGHTLY").apply()
        }
        if (nightlyResult.isFailure) {
            android.util.Log.e("LunaFetchEngine", "Update to NIGHTLY failed: ${nightlyResult.exceptionOrNull()?.message}", nightlyResult.exceptionOrNull())
            runCatching {
                android.util.Log.d("LunaFetchEngine", "Falling back to STABLE channel...")
                val res = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
                android.util.Log.d("LunaFetchEngine", "Update to STABLE status: $res, version is now: ${YoutubeDL.version(context)}")
                preferences.edit().putLong("lastYtdlpUpdate", now).putString("lastYtdlpChannel", "STABLE").apply()
            }.onFailure {
                android.util.Log.e("LunaFetchEngine", "Update to STABLE also failed: ${it.message}", it)
            }
        }
    }

    private fun copyToTree(sources: List<File>, treeUri: Uri): List<Uri> {
        val resolver = context.contentResolver
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            sources.map { source ->
                val created = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    mimeType(source.extension),
                    source.name,
                ) ?: throw DownloadException("Android no permitió crear el archivo en la carpeta elegida.")
                resolver.openOutputStream(created, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw DownloadException("Android no permitió escribir el archivo descargado.")
                created
            }
        } catch (se: SecurityException) {
            throw DownloadException("Permiso de almacenamiento revocado o carpeta inaccesible. Vuelve a seleccionarla en Luna Fetch.", se)
        } catch (iae: IllegalArgumentException) {
            throw DownloadException("La carpeta seleccionada ya no es válida. Vuelve a seleccionarla en Luna Fetch.", iae)
        }
    }

    private fun String.toVideoInfo(url: String): VideoInfo {
        val root = Json.parseToJsonElement(this).jsonObject
        val rawEntries = root["entries"]?.jsonArray ?: JsonArray(emptyList())
        val entries = rawEntries.mapIndexedNotNull { index, entry ->
            entry.jsonObject.let {
                CollectionEntry(
                    index = it.int("playlist_index", index + 1),
                    title = it.string("title").ifBlank { return@let null },
                    uploader = it.string("uploader").ifBlank { it.string("channel") },
                    durationSeconds = it.double("duration"),
                )
            }
        }
        val isCollection = entries.size > 1
        val thumbnail = if (isCollection) {
            root.thumbnail().ifBlank { rawEntries.firstYoutubeThumbnail() }
        } else {
            root.thumbnail()
        }
        return VideoInfo(
            url = url,
            title = root.string("title").ifBlank { "Título desconocido" },
            uploader = root.string("uploader").ifBlank { root.string("channel").ifBlank { "Autor desconocido" } },
            durationSeconds = root.double("duration"),
            thumbnailUrl = thumbnail,
            maxHeight = root["formats"]?.jsonArray?.maxOfOrNull { it.jsonObject.int("height") }
                ?.takeIf { it > 0 } ?: root.int("height", 1080),
            collectionTitle = root.string("playlist_title").ifBlank { if (isCollection) root.string("title") else "" }
                .ifBlank { null },
            collectionCount = root.int("playlist_count", entries.size),
            collectionEntries = entries,
        )
    }

    private fun JsonArray.firstThumbnail(): String =
        firstOrNull()?.jsonObject?.thumbnail().orEmpty()

    private fun JsonArray.firstYoutubeThumbnail(): String =
        firstOrNull()?.jsonObject?.string("id")
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            .orEmpty()

    private fun JsonObject.thumbnail(): String {
        val directThumbnail = string("thumbnail")
        if (directThumbnail.isNotBlank() && (!directThumbnail.contains("/s_p/") || directThumbnail.contains("?"))) {
            return directThumbnail
        }
        val list = this["thumbnails"]?.jsonArray
            ?.mapNotNull {
                val u = it.jsonObject.string("url")
                if (u.contains("/s_p/") && !u.contains("?")) null else u.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        return list.lastOrNull() ?: directThumbnail
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(name: String, fallback: Int = 0): Int =
        this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback

    private fun JsonObject.double(name: String): Double =
        this[name]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

    private fun String.firstCollectionVideoUrl(): String? {
        val entry = Json.parseToJsonElement(this).jsonObject["entries"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: return null
        return entry.string("webpage_url")
            .ifBlank { entry.string("original_url") }
            .ifBlank {
                entry.string("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
            }
            .ifBlank {
                entry.string("id").ifBlank { entry.string("url") }
                    .takeIf { it.isNotBlank() }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
                    .orEmpty()
            }
            .takeIf { it.isNotBlank() }
    }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    private companion object {
        const val UpdateIntervalMillis = 24L * 60L * 60L * 1000L
    }
}
