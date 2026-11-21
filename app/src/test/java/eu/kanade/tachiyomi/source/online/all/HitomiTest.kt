package eu.kanade.tachiyomi.source.online.all

import okhttp3.HttpUrl.Companion.toHttpUrl
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

    @Test
    fun `big thumbnail urls are rewritten to small thumbnail urls`() {
        val avifResult = Hitomi.rewriteBigThumbnailUrlToSmall(
            "https://btn.gold-usergeneratedcontent.net/avifbigtn/6/dd/fe941ccc.avif".toHttpUrl(),
        )
        val webpResult = Hitomi.rewriteBigThumbnailUrlToSmall(
            "https://atn.gold-usergeneratedcontent.net/webpbigtn/a/c1/45325eb9.webp".toHttpUrl(),
        )

        assertEquals(
            "https://btn.gold-usergeneratedcontent.net/avifsmalltn/6/dd/fe941ccc.avif",
            avifResult?.toString(),
        )
        assertEquals(
            "https://atn.gold-usergeneratedcontent.net/webpsmalltn/a/c1/45325eb9.webp",
            webpResult?.toString(),
        )
    }

    @Test
    fun `already small or non thumbnail urls are not rewritten`() {
        assertNull(
            Hitomi.rewriteBigThumbnailUrlToSmall(
                "https://btn.gold-usergeneratedcontent.net/avifsmalltn/6/dd/fe941ccc.avif".toHttpUrl(),
            ),
        )
        assertNull(
            Hitomi.rewriteBigThumbnailUrlToSmall(
                "https://a1.gold-usergeneratedcontent.net/123/456/fe941ccc.avif".toHttpUrl(),
            ),
        )
    }
}
