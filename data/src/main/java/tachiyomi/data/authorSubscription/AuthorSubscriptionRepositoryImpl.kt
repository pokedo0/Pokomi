package tachiyomi.data.authorSubscription

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.manga.model.Manga

class AuthorSubscriptionRepositoryImpl(
    private val handler: DatabaseHandler,
) : AuthorSubscriptionRepository {

    override fun subscribeAll(): Flow<List<AuthorSubscription>> {
        return handler.subscribeToList {
            author_subscriptionQueries.selectAll(AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getAll(): List<AuthorSubscription> {
        return handler.awaitList {
            author_subscriptionQueries.selectAll(AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getById(id: Long): AuthorSubscription? {
        return handler.awaitOneOrNull {
            author_subscriptionQueries.selectById(id, AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? {
        return handler.awaitOneOrNull {
            author_subscriptionQueries.selectByNormalizedQuery(normalizedQuery, AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long {
        val now = System.currentTimeMillis()
        return handler.await(true) {
            val existing = handler.awaitOneOrNull {
                author_subscriptionQueries.selectByNormalizedQuery(normalizedQuery, AuthorSubscriptionMapper::map)
            }

            if (existing == null) {
                handler.awaitOneExecutable(true) {
                    author_subscriptionQueries.insert(
                        source = source,
                        name = name,
                        query = query,
                        normalizedQuery = normalizedQuery,
                        createdAt = now,
                        updatedAt = now,
                        lastRefreshAt = null,
                    )
                    author_subscriptionQueries.selectLastInsertedRowId()
                }
            } else {
                author_subscriptionQueries.updateSource(
                    source = source,
                    name = name,
                    query = query,
                    updatedAt = now,
                    normalizedQuery = normalizedQuery,
                )
                existing.id
            }
        }
    }

    override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) {
        handler.await {
            author_subscriptionQueries.updateLastRefreshAt(
                lastRefreshAt = lastRefreshAt,
                updatedAt = System.currentTimeMillis(),
                id = id,
            )
        }
    }

    override suspend fun getResultCaches(
        subscriptionIds: Collection<Long>,
    ): List<AuthorSubscriptionResultCache> {
        if (subscriptionIds.isEmpty()) return emptyList()

        val caches = handler.awaitList {
            author_subscription_result_cacheQueries.selectBySubscriptionIds(subscriptionIds)
        }
        if (caches.isEmpty()) return emptyList()

        val mangas = handler.awaitList {
            author_subscription_result_cacheQueries.selectMangasBySubscriptionIds(
                subscriptionIds = subscriptionIds,
                mapper = {
                        subscriptionId,
                        id,
                        source,
                        url,
                        artist,
                        author,
                        description,
                        genre,
                        title,
                        status,
                        thumbnailUrl,
                        favorite,
                        lastUpdate,
                        nextUpdate,
                        initialized,
                        viewer,
                        chapterFlags,
                        coverLastModified,
                        dateAdded,
                        filteredScanlators,
                        updateStrategy,
                        calculateInterval,
                        lastModifiedAt,
                        favoriteModifiedAt,
                        version,
                        isSyncing,
                        notes,
                    ->
                    CacheMangaRow(
                        subscriptionId = subscriptionId,
                        manga = MangaMapper.mapManga(
                            id = id,
                            source = source,
                            url = url,
                            artist = artist,
                            author = author,
                            description = description,
                            genre = genre,
                            title = title,
                            status = status,
                            thumbnailUrl = thumbnailUrl,
                            favorite = favorite,
                            lastUpdate = lastUpdate,
                            nextUpdate = nextUpdate,
                            initialized = initialized,
                            viewerFlags = viewer,
                            chapterFlags = chapterFlags,
                            coverLastModified = coverLastModified,
                            dateAdded = dateAdded,
                            filteredScanlators = filteredScanlators,
                            updateStrategy = updateStrategy,
                            calculateInterval = calculateInterval,
                            lastModifiedAt = lastModifiedAt,
                            favoriteModifiedAt = favoriteModifiedAt,
                            version = version,
                            isSyncing = isSyncing,
                            notes = notes,
                        ),
                    )
                },
            )
        }
            .groupBy { it.subscriptionId }

        return caches.mapNotNull { cache ->
            val cachedMangas = mangas[cache.subscription_id].orEmpty().map { it.manga }
            if (cachedMangas.size != cache.result_count.toInt()) {
                return@mapNotNull null
            }

            AuthorSubscriptionResultCache(
                subscriptionId = cache.subscription_id,
                mangas = cachedMangas,
                cachedAt = cache.cached_at,
            )
        }
    }

    override suspend fun upsertResultCache(subscriptionId: Long, mangaIds: List<Long>, cachedAt: Long) {
        handler.await(inTransaction = true) {
            author_subscription_result_cacheQueries.deleteBySubscriptionId(subscriptionId)
            author_subscription_result_cacheQueries.insert(
                subscriptionId = subscriptionId,
                cachedAt = cachedAt,
                resultCount = mangaIds.size.toLong(),
            )
            mangaIds.forEachIndexed { index, mangaId ->
                author_subscription_result_cacheQueries.insertManga(
                    subscriptionId = subscriptionId,
                    mangaId = mangaId,
                    position = index.toLong(),
                )
            }
        }
    }

    override suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>) {
        if (updates.isEmpty()) return

        val updatedAt = System.currentTimeMillis()
        handler.await(inTransaction = true) {
            updates.forEach { update ->
                author_subscriptionQueries.updateOrder(
                    sortOrder = update.sortOrder,
                    pinned = update.pinned.toLong(),
                    updatedAt = updatedAt,
                    id = update.id,
                )
            }
        }
    }

    override suspend fun updatePinned(id: Long, pinned: Boolean) {
        handler.await {
            author_subscriptionQueries.updatePinned(
                pinned = pinned.toLong(),
                updatedAt = System.currentTimeMillis(),
                id = id,
            )
        }
    }

    private fun Boolean.toLong(): Long {
        return if (this) 1L else 0L
    }

    override suspend fun deleteById(id: Long) {
        handler.await { author_subscriptionQueries.deleteById(id) }
    }

    override suspend fun deleteByNormalizedQuery(normalizedQuery: String) {
        handler.await { author_subscriptionQueries.deleteByNormalizedQuery(normalizedQuery) }
    }

    private data class CacheMangaRow(
        val subscriptionId: Long,
        val manga: Manga,
    )
}
