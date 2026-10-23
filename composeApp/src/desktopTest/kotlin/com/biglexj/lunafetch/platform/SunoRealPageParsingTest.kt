package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.SunoExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica el análisis del payload real de Suno.
 *
 * La captura `suno_song_page.html` es una página de Suno tal y como la sirve
 * hoy el sitio (React Server Components con cadenas escapadas), recortada a
 * la ficha de la canción y a su letra.
 */
class SunoRealPageParsingTest {

    private val songId = "6a011fc0-5d07-4810-b422-b3f8af4032fc"

    private fun capturedPage(): String =
        checkNotNull(javaClass.getResourceAsStream("/suno_song_page.html")) {
            "Falta la captura de la página de Suno en los recursos de prueba."
        }.bufferedReader().use { it.readText() }

    @Test
    fun parsesRealSunoPayload() {
        val meta = SunoExtractor.parseMetadata(capturedPage(), songId)

        assertEquals("Y Que Pasó", meta.title)
        assertEquals("Biglex J", meta.displayName)
        assertEquals("biglexj", meta.creatorHandle)
        assertTrue(meta.durationSeconds > 100.0, "La duración debe salir del payload")
        assertEquals("https://cdn2.suno.ai/image_large_$songId.jpeg", meta.coverUrl)
        assertTrue(meta.tags.contains("Latin pop"), "El estilo se lee del campo de etiquetas")
        assertTrue(meta.lyricsPrompt.contains("(Ely... para tu corazón)"), "La letra llega en un fragmento del payload")
        assertTrue(meta.lyricsPrompt.contains("[Verse 1 - Female Vocal]"))
        assertTrue(!meta.lyricsPrompt.contains("\\n"), "Los saltos de línea deben quedar decodificados")
    }
}
