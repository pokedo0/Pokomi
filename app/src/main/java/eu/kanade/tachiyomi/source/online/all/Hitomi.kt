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
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat

class Hitomi(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    PagePreviewSource {

    override val lang = delegate.lang

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
        ensureDelegateCompatible()
        val chapter = chapters.firstOrNull() ?: SChapter.create().apply {
            url = manga.url
        }
        logcat(LogPriority.DEBUG) { "Fetching Hitomi page previews via delegate.getPageList(url=${chapter.url})" }
        val pages = delegate.getPageList(chapter)
        val previews = pages.mapIndexedNotNull { index, chapterPage ->
            val previewUrl = toHitomiPreviewUrl(chapterPage.imageUrl) ?: return@mapIndexedNotNull null
            PagePreviewInfo(
                index + 1,
                imageUrl = previewUrl,
            )
        }
        if (previews.isEmpty()) {
            logcat(LogPriority.WARN) {
                "Loaded 0 Hitomi page previews from ${pages.size} delegate pages for ${manga.url}"
            }
        } else {
            logcat(LogPriority.DEBUG) {
                "Loaded ${previews.size} Hitomi page previews via delegate for ${manga.url}"
            }
        }

        return PagePreviewPage(
            page,
            previews,
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

    companion object {
        private const val IMAGE_LOOPBACK_HOST = "127.0.0.1"
        private const val IMAGE_THUMBNAIL_PARAM = "is_thumbnail"

        internal fun toHitomiPreviewUrl(rawUrl: String?): String? {
            val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val httpUrl = trimmed.toHttpUrlOrNull() ?: return trimmed
            if (httpUrl.host != IMAGE_LOOPBACK_HOST) {
                return trimmed
            }
            return httpUrl.newBuilder()
                .setQueryParameter(IMAGE_THUMBNAIL_PARAM, "true")
                .build()
                .toString()
        }
    }
}
