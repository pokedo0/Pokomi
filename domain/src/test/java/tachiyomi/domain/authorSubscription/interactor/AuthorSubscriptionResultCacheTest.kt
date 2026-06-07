package tachiyomi.domain.authorSubscription.interactor

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.manga.model.Manga

class AuthorSubscriptionResultCacheTest {

    @Test
    fun `get cache returns empty without hitting repository when ids are empty`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()

        GetAuthorSubscriptionResultCache(repository).await(emptyList()) shouldBe emptyList()

        repository.requestedCacheIds shouldBe emptyList()
    }

    @Test
    fun `get cache requests all ids from repository`() = runTest {
        val repository = FakeAuthorSubscriptionRepository(
            caches = listOf(
                AuthorSubscriptionResultCache(
                    subscriptionId = 1,
                    mangas = listOf(manga(id = 10)),
                    cachedAt = 100,
                ),
            ),
        )

        val caches = GetAuthorSubscriptionResultCache(repository).await(listOf(1, 2))

        repository.requestedCacheIds shouldContainExactly listOf(1L, 2L)
        caches.single().subscriptionId shouldBe 1
    }

    @Test
    fun `upsert cache stores manga ids in result order`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val mangas = listOf(manga(id = 10), manga(id = 20), manga(id = 30))

        UpsertAuthorSubscriptionResultCache(repository).await(
            subscriptionId = 5,
            mangas = mangas,
            cachedAt = 123,
        )

        repository.upserts.single() shouldBe CacheUpsert(
            subscriptionId = 5,
            mangaIds = listOf(10, 20, 30),
            cachedAt = 123,
        )
    }

    private fun manga(id: Long): Manga {
        return Manga.create().copy(
            id = id,
            source = 1,
            url = "/manga/$id",
            ogTitle = "Manga $id",
        )
    }

    private data class CacheUpsert(
        val subscriptionId: Long,
        val mangaIds: List<Long>,
        val cachedAt: Long,
    )

    private class FakeAuthorSubscriptionRepository(
        private val caches: List<AuthorSubscriptionResultCache> = emptyList(),
    ) : AuthorSubscriptionRepository {

        var requestedCacheIds = emptyList<Long>()
        val upserts = mutableListOf<CacheUpsert>()

        override fun subscribeAll(): Flow<List<AuthorSubscription>> = emptyFlow()

        override suspend fun getAll(): List<AuthorSubscription> = emptyList()

        override suspend fun getById(id: Long): AuthorSubscription? = null

        override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? = null

        override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long = 0

        override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) = Unit

        override suspend fun replaceAll(subscriptions: List<AuthorSubscription>) = Unit

        override suspend fun getResultCaches(
            subscriptionIds: Collection<Long>,
        ): List<AuthorSubscriptionResultCache> {
            requestedCacheIds = subscriptionIds.toList()
            return caches
        }

        override suspend fun upsertResultCache(subscriptionId: Long, mangaIds: List<Long>, cachedAt: Long) {
            upserts += CacheUpsert(subscriptionId, mangaIds, cachedAt)
        }

        override suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>) = Unit

        override suspend fun updatePinned(id: Long, pinned: Boolean) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteByNormalizedQuery(normalizedQuery: String) = Unit
    }
}
