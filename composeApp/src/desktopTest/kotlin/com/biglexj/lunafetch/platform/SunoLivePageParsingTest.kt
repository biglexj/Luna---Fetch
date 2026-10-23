package com.biglexj.lunafetch.platform

import com.biglexj.lunafetch.domain.SunoExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica el análisis de una página real y **completa** de Suno.
 *
 * La captura `suno_song_page_live.html` es la página tal y como la sirve hoy
 * el sitio para una canción pública (React Server Components con cadenas
 * escapadas), sin recortar. La diferencia con la captura recortada es
 * precisamente donde se pierde la letra: el fragmento de texto (`$c7`) vive
 * lejos del ancla de la canción.
 */
class SunoLivePageParsingTest {

    private val songId = "5805f8de-5894-4dda-87b5-8fc479f41333"

    private fun livePage(): String =
        checkNotNull(javaClass.getResourceAsStream("/suno_song_page_live.html")) {
            "Falta la captura de la página de Suno en los recursos de prueba."
        }.bufferedReader().use { it.readText() }

    @Test
    fun parsesCompleteSunoPayload() {
        val meta = SunoExtractor.parseMetadata(livePage(), songId)

        assertEquals("¿Dónde quedó nuestro amor?", meta.title)
        assertEquals("Biglex J", meta.displayName)
        assertEquals("biglexj", meta.creatorHandle)
        assertTrue(meta.durationSeconds > 100.0, "La duración debe salir del payload")
        assertTrue(meta.tags.isNotBlank(), "El estilo se lee del campo de etiquetas")
        assertTrue(
            meta.lyricsPrompt.contains("(Ely... para tu corazón)"),
            "La letra debe llegar desde el fragmento del payload: ${meta.lyricsPrompt.take(120)}",
        )
        assertTrue(
            meta.lyricsPrompt.contains("[Verse 1"),
            "La letra debe conservar sus secciones: ${meta.lyricsPrompt.take(120)}",
        )
        assertTrue(!meta.isInstrumental, "Una canción con letra no es instrumental")
    }

    /**
     * Una canción instrumental llega con la letra vacía **por diseño** de
     * Suno (`make_instrumental: true`). El analizador debe distinguir ese
     * caso del anómalo, para que la interfaz no lo presente como un fallo.
     */
    @Test
    fun detectsInstrumentalSongsWithoutLyrics() {
        val page = checkNotNull(javaClass.getResourceAsStream("/suno_song_page_instrumental.html")) {
            "Falta la captura de la canción instrumental en los recursos de prueba."
        }.bufferedReader().use { it.readText() }

        val meta = SunoExtractor.parseMetadata(page, "1d286dd7-6549-4698-a295-c75a27a50fdd")

        assertEquals("Weightless Sleep", meta.title)
        assertTrue(meta.isInstrumental, "La canción debe reconocerse como instrumental")
        assertTrue(meta.lyricsPrompt.isBlank(), "Una canción instrumental no trae letra")
    }
}
