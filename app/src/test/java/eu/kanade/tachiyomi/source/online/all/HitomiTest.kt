package eu.kanade.tachiyomi.source.online.all

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HitomiTest {

    @Test
    fun `loopback page url adds thumbnail query parameter while preserving gif flag and hash fragment`() {
        val result = Hitomi.toHitomiPreviewUrl(
            "https://127.0.0.1/?is_gif=false#f23a29a32419b1b6f8069405beaf75cccf64b5379fa7b147d826c8dddb7ff413",
        )

        assertEquals(
            "https://127.0.0.1/?is_gif=false&is_thumbnail=true#f23a29a32419b1b6f8069405beaf75cccf64b5379fa7b147d826c8dddb7ff413",
            result,
        )
    }

    @Test
    fun `loopback url replaces existing thumbnail query parameter with true`() {
        val result = Hitomi.toHitomiPreviewUrl("https://127.0.0.1/?is_gif=true&is_thumbnail=small#abc123")

        assertEquals("https://127.0.0.1/?is_gif=true&is_thumbnail=true#abc123", result)
    }

    @Test
    fun `non loopback url is returned unchanged`() {
        val directUrl = "https://atn.example.net/avifbigtn/1/23/abc123.avif"

        assertEquals(directUrl, Hitomi.toHitomiPreviewUrl(directUrl))
    }

    @Test
    fun `blank or null url returns null`() {
        assertNull(Hitomi.toHitomiPreviewUrl(null))
        assertNull(Hitomi.toHitomiPreviewUrl("   "))
    }
}
