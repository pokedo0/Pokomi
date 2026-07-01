package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.DelegatedHttpSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Response

class Hitomi(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    PagePreviewSource {

    override val lang = delegate.lang

    private val cdnDomain = "gold-usergeneratedcontent.net"
    private val ltnUrl = "https://ltn.$cdnDomain"

    override suspend fun getPagePreviewList(
        manga: SManga,
        chapters: List<SChapter>,
        page: Int,
    ): PagePreviewPage {
        val gallery = client.newCall(mangaDetailsRequest(manga))
            .awaitSuccess()
            .parseGallery()

        return PagePreviewPage(
            page,
            gallery.files.mapIndexed { index, imageFile ->
                PagePreviewInfo(
                    index + 1,
                    imageUrl = thumbnailUrl(imageFile),
                )
            },
            false,
            1,
        )
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        return client.newCachelessCallWithProgress(
            if (cacheControl != null) {
                GET(page.imageUrl, headers, cacheControl)
            } else {
                GET(page.imageUrl, headers)
            },
            page,
        ).awaitSuccess()
    }

    private fun Response.parseGallery(): Gallery {
        return use {
            json.decodeFromString(
                body.string().substringAfter("var galleryinfo = "),
            )
        }
    }

    private suspend fun thumbnailUrl(imageFile: ImageFile): String {
        val hash = imageFile.hash
        val type = if (imageFile.isGif) "webp" else "avif"
        val subDomain = "${'a' + subdomainOffset(imageIdFromHash(hash))}tn"

        return "https://$subDomain.$cdnDomain/${type}smalltn/${thumbPathFromHash(hash)}/$hash.$type"
    }

    private suspend fun refreshScript() = mutex.withLock {
        if (scriptLastRetrieval == null || (scriptLastRetrieval!! + 60000) < System.currentTimeMillis()) {
            val ggScript = client.newCall(
                GET("$ltnUrl/gg.js?_=${System.currentTimeMillis()}", headers),
            ).awaitSuccess().use { it.body.string() }

            subdomainOffsetDefault = Regex("var o = (\\d)").find(ggScript)!!.groupValues[1].toInt()
            val o = Regex("o = (\\d); break;").find(ggScript)!!.groupValues[1].toInt()

            subdomainOffsetMap.clear()
            Regex("case (\\d+):").findAll(ggScript).forEach {
                val case = it.groupValues[1].toInt()
                subdomainOffsetMap[case] = o
            }

            scriptLastRetrieval = System.currentTimeMillis()
        }
    }

    private suspend fun subdomainOffset(imageId: Int): Int {
        refreshScript()
        return subdomainOffsetMap[imageId] ?: subdomainOffsetDefault
    }

    private fun imageIdFromHash(hash: String): Int {
        val match = Regex("(..)(.)$").find(hash)
        return match!!.groupValues.let { it[2] + it[1] }.toInt(16)
    }

    private fun thumbPathFromHash(hash: String): String {
        return hash.replace(Regex("""^.*(..)(.)$"""), "$2/$1")
    }

    @Serializable
    data class Gallery(
        val files: List<ImageFile> = emptyList(),
    )

    @Serializable
    data class ImageFile(
        val hash: String,
        private val name: String,
    ) {
        val isGif get() = name.endsWith(".gif") || name.endsWith(".webp")
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
        private val mutex = Mutex()
        private var scriptLastRetrieval: Long? = null
        private var subdomainOffsetDefault = 0
        private val subdomainOffsetMap = mutableMapOf<Int, Int>()
    }
}
