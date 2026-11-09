package com.biglexj.lunafetch.domain

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Gestor de descifrado seguro de audio para canciones privadas/compartidas de Suno AI.
 *
 * Suno protege el stream canónico de pistas privadas y compartidas entregando
 * un flujo Opus en contenedor M4A cifrado con AES-CTR en CloudFront (d2lwuy8qc234o3.cloudfront.net),
 * y un servicio de autorización (Mango Rights) en studio-api.prod.suno.com.
 *
 * Este descifrador solicita los derechos (key, iv, glt), deriva la clave de usuario
 * mediante SHA-256(glt), desencripta la clave de contenido con AES-GCM (usando el clipId
 * como AAD), y descifra el stream en tiempo real hacia el fichero de salida.
 */
object SunoMangoDecryptor {

    const val MANGO_CDN_HOST = "d2lwuy8qc234o3.cloudfront.net"
    const val MANGO_STREAM_URL_PREFIX = "https://$MANGO_CDN_HOST/1/clip/"

    data class MangoRights(
        val key: String,
        val iv: String,
        val glt: String,
    )

    fun streamUrlForId(clipId: String): String = "$MANGO_STREAM_URL_PREFIX$clipId.m4a"

    /**
     * Solicita la autorización de reproducción Mango Rights para un clip.
     */
    fun fetchRights(clipId: String, cookieHeader: String? = null): MangoRights? {
        return runCatching {
            val url = URL("https://studio-api.prod.suno.com/api/mango/rights")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                setRequestProperty("Origin", "https://suno.com")
                setRequestProperty("Referer", "https://suno.com/")
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
            }
            val payload = """{"content_params":{"content_id":"$clipId","content_type":"clip"}}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode !in 200..299) return null

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val key = Regex(""""key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return null
            val iv = Regex(""""iv"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return null
            val glt = Regex(""""glt"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return null

            MangoRights(key = key, iv = iv, glt = glt)
        }.getOrNull()
    }

    /**
     * Descifra el stream de audio AES-CTR en tiempo real escribiéndolo en [outputStream].
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decryptStream(
        clipId: String,
        rights: MangoRights,
        inputStream: InputStream,
        outputStream: OutputStream,
        bufferSize: Int = 64 * 1024,
        onBytesDecrypted: ((bytesCount: Long) -> Unit)? = null,
    ) {
        val md = MessageDigest.getInstance("SHA-256")
        val userKeyBytes = md.digest(rights.glt.toByteArray(Charsets.UTF_8))
        val userKey = SecretKeySpec(userKeyBytes, "AES")

        val rawKey = unwrapSecret(rights.key, clipId, userKey)
        val rawIv = unwrapSecret(rights.iv, clipId, userKey)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rawKey, "AES"), IvParameterSpec(rawIv))

        val buffer = ByteArray(bufferSize)
        var read: Int
        var totalDecrypted = 0L

        while (inputStream.read(buffer).also { read = it } != -1) {
            val decrypted = cipher.update(buffer, 0, read)
            if (decrypted != null && decrypted.isNotEmpty()) {
                outputStream.write(decrypted)
                totalDecrypted += decrypted.size
                onBytesDecrypted?.invoke(totalDecrypted)
            }
        }

        val finalBytes = cipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) {
            outputStream.write(finalBytes)
            totalDecrypted += finalBytes.size
            onBytesDecrypted?.invoke(totalDecrypted)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun unwrapSecret(b64: String, clipId: String, userKey: SecretKeySpec): ByteArray {
        val raw = Base64.Default.decode(b64)
        if (raw.size < 28) throw IllegalArgumentException("Datos de autorización incompletos para Suno Mango")

        val iv = raw.copyOfRange(0, 12)
        val cipherText = raw.copyOfRange(12, raw.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, userKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(clipId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(cipherText)
    }
}
