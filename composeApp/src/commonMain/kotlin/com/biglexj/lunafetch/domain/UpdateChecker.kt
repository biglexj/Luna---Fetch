package com.biglexj.lunafetch.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateRelease(
    val version: String,
    val downloadUrl: String,
    val exeDownloadUrl: String = "",
    val releasePageUrl: String,
    val body: String = "",
)

object UpdateChecker {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun parseUpdateRelease(json: String): UpdateRelease? {
        val root = runCatching { jsonParser.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val htmlUrl = root["html_url"]?.jsonPrimitive?.content ?: return null
        val body = root["body"]?.jsonPrimitive?.content.orEmpty()

        val assets = root["assets"]?.jsonArray.orEmpty()
        val apkAsset = assets.firstOrNull { asset ->
            val url = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
            val name = asset.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
            url.endsWith(".apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
        }
        val exeAsset = assets.firstOrNull { asset ->
            val url = asset.jsonObject["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
            val name = asset.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
            url.endsWith(".exe", ignoreCase = true) || name.endsWith(".exe", ignoreCase = true) || name.endsWith(".msi", ignoreCase = true)
        }

        val apkUrl = apkAsset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content ?: htmlUrl
        val exeUrl = exeAsset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content.orEmpty()

        val cleanVersion = tagName.removePrefix("v").trim()
        return UpdateRelease(
            version = cleanVersion,
            downloadUrl = apkUrl,
            exeDownloadUrl = exeUrl,
            releasePageUrl = htmlUrl,
            body = body,
        )
    }

    fun isNewerVersion(current: String, remote: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (c > r) return false
        }
        return false
    }
}
