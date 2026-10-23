package com.biglexj.lunafetch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifica el análisis del payload de Suno sin depender de la red: se
 * reproduce la estructura real que sirve Suno (React Server Components con
 * cadenas escapadas) dentro del HTML.
 */
class SunoMetadataParsingTest {

    private val songId = "20f569e1-fb1b-4b4e-af8e-8518ffb5837a"

    /**
     * Reproduce una clave del payload de Suno. En el HTML real las cadenas
     * viajan con las comillas escapadas (`\"clave\":\"valor\"`) porque viven
     * dentro de un literal de JavaScript.
     */
    private fun field(key: String, value: String): String = "\\\"$key\\\":\\\"$value\\\""

    /** Los valores numéricos viajan sin comillas en el payload real. */
    private fun numberField(key: String, value: String): String = "\\\"$key\\\":$value"

    private fun clipPayload(): String = buildString {
        append(field("title", "Hello!")).append(",")
        append(field("play_count", "9066")).append(",")
        append(field("id", songId)).append(",")
        append(field("entity_type", "song_schema")).append(",")
        append(field("video_url", "https://cdn1.suno.ai/$songId.mp4")).append(",")
        append(field("audio_url", "https://studio-api.prod.suno.com/api/forbidden")).append(",")
        append(field("media_urls", "")).append(",")
        append(field("image_url", "https://cdn2.suno.ai/image_$songId.jpeg")).append(",")
        append(field("image_large_url", "https://cdn2.suno.ai/image_large_$songId.jpeg")).append(",")
        append(field("major_model_version", "v5.5")).append(",")
        append(field("model_name", "chirp-fenix")).append(",")
        append("{")
        append(field("tags", "Bright, playful global pop, 120 BPM")).append(",")
        // La letra viaja con los saltos de línea escapados.
        append(field("prompt", "[Verse 1]\\n你好\\nこんにちは\\n[Verse 2]\\nHola")).append(",")
        append(field("type", "gen")).append(",")
        append(numberField("duration", "62.44"))
        append("},")
        append(field("is_liked", "false")).append(",")
        append(field("user_id", "d0df2b65-aaad-4ddf-8ba8-b44e5441ba3f")).append(",")
        // Personas de Suno: comparten la clave `display_name` pero no el alias.
        append(field("display_name", "V6")).append(",")
        append(field("display_name", "V6-MINI")).append(",")
        append(field("display_name", "COVER")).append(",")
        append(field("display_name", "Tony")).append(",")
        append(field("handle", "listentolovelymusic")).append(",")
        append(field("created_at", "2026-09-08T12:18:28.692Z")).append(",")
        append(field("display_tags", "global pop, pop"))
        append("}")
    }

    private fun html(): String = buildString {
        append("<!DOCTYPE html><html><head>")
        append("<title>Hello! by Tony | Suno</title>")
        append("<meta name=\"description\" content=\"Hello! by Tony (@listentolovelymusic). Listen and make your own on Suno.\"/>")
        append("<link rel=\"preload\" as=\"image\" href=\"https://cdn2.suno.ai/image_large_$songId.jpeg\"/>")
        append("</head><body>")
        // Menú de la app: claves JSON idénticas a las de la ficha. El anclaje
        // en el identificador evita que el título del menú gane la carrera.
        append(field("title", "Get the full app experience")).append(",")
        append(field("subtitle", "Unlock the joy of making music"))
        append("<script>self.__next_f.push([1,\"" + clipPayload() + "\"])</script>")
        append("</body></html>")
    }

    @Test
    fun extractsSongMetadataFromPayload() {
        val meta = SunoExtractor.parseMetadata(html(), songId)

        assertEquals("Hello!", meta.title)
        assertEquals("Tony", meta.displayName)
        assertEquals("listentolovelymusic", meta.creatorHandle)
        assertEquals(62.44, meta.durationSeconds, 0.01)
        assertEquals("https://cdn2.suno.ai/image_large_$songId.jpeg", meta.coverUrl)
        assertTrue(meta.tags.contains("global pop"))
        assertTrue(meta.lyricsPrompt.contains("你好"))
        assertTrue(meta.lyricsPrompt.contains("[Verse 2]"))
        assertTrue(!meta.lyricsPrompt.contains("\\n"))
    }

    @Test
    fun fallsBackToHtmlHeaderWhenPayloadIsMissing() {
        val withoutPayload = "<html><head><title>Y Que Pasó by Biglex J | Suno</title>" +
            "<meta name=\"description\" content=\"Y Que Pasó by Biglex J (@biglexj). Listen and make your own on Suno.\"/>" +
            "</head><body></body></html>"

        val meta = SunoExtractor.parseMetadata(withoutPayload, songId)

        assertEquals("Y Que Pasó", meta.title)
        assertEquals("Biglex J", meta.displayName)
        assertEquals("biglexj", meta.creatorHandle)
        assertEquals("https://cdn2.suno.ai/image_large_$songId.jpeg", meta.coverUrl)
    }

    @Test
    fun keepsGenericTitleWhenNothingIsAvailable() {
        val meta = SunoExtractor.parseMetadata("<html><body></body></html>", songId)

        assertEquals("Canción Suno 20f569e1", meta.title)
        assertEquals("Suno AI", meta.displayName)
        assertEquals(120.0, meta.durationSeconds, 0.01)
    }
}
