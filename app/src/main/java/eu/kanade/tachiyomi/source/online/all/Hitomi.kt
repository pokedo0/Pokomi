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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.DelegatedHttpSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat

class Hitomi(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    PagePreviewSource {

    override val lang = delegate.lang

    private val cdnDomain = "gold-usergeneratedcontent.net"
    private val ltnUrl = "https://ltn.$cdnDomain"

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        ensureDelegateCompatible()
        return delegate.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }

    override suspend fun getPagePreviewList(
        manga: SManga,
        chapters: List<SChapter>,
        page: Int,
    ): PagePreviewPage {
        val request = galleryInfoRequest(manga)
        logcat(LogPriority.DEBUG) { "Fetching Hitomi page previews from ${request.url}" }
        val gallery = client.newCall(request)
            .awaitSuccess()
            .parseGallery()

        logcat(LogPriority.DEBUG) { "Loaded ${gallery.files.size} Hitomi page previews for ${manga.url}" }
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

    private fun galleryInfoRequest(manga: SManga): Request {
        val galleryId = extractGalleryId(manga.url)
        return if (galleryId != null) {
            GET("$ltnUrl/galleries/$galleryId.js", headers)
        } else {
            logcat(LogPriority.WARN) { "Failed to extract Hitomi gallery id from url=${manga.url}, falling back to delegate mangaDetailsRequest" }
            mangaDetailsRequest(manga)
        }
    }

    private fun extractGalleryId(url: String): String? {
        return url
            .substringBefore("?")
            .substringBefore("#")
            .trimEnd('/')
            .substringAfterLast("/")
            .substringAfterLast("-")
            .substringBefore(".")
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
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
