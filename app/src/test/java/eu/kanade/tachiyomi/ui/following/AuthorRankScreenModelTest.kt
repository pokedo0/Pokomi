package eu.kanade.tachiyomi.ui.following

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.authorSubscription.interactor.DeleteAuthorSubscription
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.ReorderAuthorSubscriptions
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorRankScreenModelTest {

    @Test
    fun `remove author stages deletion and normalizes remaining order`() = runTest {
        val repository = FakeAuthorSubscriptionRepository(
            authors = listOf(
                author(id = 1, sortOrder = 0),
                author(id = 2, sortOrder = 1),
                author(id = 3, sortOrder = 2),
            ),
        )
        val model = model(repository)
        eventually(2.seconds) {
            model.state.value.items.map { it.id } shouldBe listOf(1L, 2L, 3L)
        }

        model.removeAuthor(2)

        model.state.value.items.map { it.id } shouldBe listOf(1L, 3L)
        model.state.value.items.map { it.sortOrder } shouldBe listOf(0L, 1L)
        repository.deletedIds shouldBe emptyList()
    }

    @Test
    fun `save persists staged deletions before saving remaining order`() {
        runBlocking {
            val repository = FakeAuthorSubscriptionRepository(
                authors = listOf(
                    author(id = 1, sortOrder = 0),
                    author(id = 2, sortOrder = 1),
                    author(id = 3, sortOrder = 2),
                ),
            )
            val model = model(repository)
            eventually(2.seconds) {
                model.state.value.items.map { it.id } shouldBe listOf(1L, 2L, 3L)
            }

            model.removeAuthor(2)
            model.save(
                onSaved = {},
                navigateUp = {},
            )

            eventually(2.seconds) {
                repository.operations shouldBe listOf(
                    "delete:2",
                    "order:1,3",
                )
            }
            repository.orderUpdates.single().map { it.id } shouldBe listOf(1L, 3L)
            repository.orderUpdates.single().map { it.sortOrder } shouldBe listOf(0L, 1L)
        }
    }

    private fun model(repository: FakeAuthorSubscriptionRepository): AuthorRankScreenModel {
        val preferences = FollowingPreferences(InMemoryPreferenceStore())
        return AuthorRankScreenModel(
            initialAuthorId = null,
            getAuthorSubscriptions = GetAuthorSubscriptions(repository),
            reorderAuthorSubscriptions = ReorderAuthorSubscriptions(repository, preferences),
            deleteAuthorSubscription = DeleteAuthorSubscription(repository, preferences),
        )
    }

    private fun author(
        id: Long,
        sortOrder: Long,
        pinned: Boolean = false,
    ): AuthorSubscription {
        return AuthorSubscription(
            id = id,
            source = 1,
            name = "Author $id",
            query = "Author $id",
            normalizedQuery = "author $id",
            createdAt = 0,
            updatedAt = 0,
            lastRefreshAt = null,
            sortOrder = sortOrder,
            pinned = pinned,
        )
    }

    private class FakeAuthorSubscriptionRepository(
        private val authors: List<AuthorSubscription>,
    ) : AuthorSubscriptionRepository {

        val deletedIds = mutableListOf<Long>()
        val operations = mutableListOf<String>()
        val orderUpdates = mutableListOf<List<AuthorSubscriptionOrderUpdate>>()

        override fun subscribeAll(): Flow<List<AuthorSubscription>> = emptyFlow()

        override suspend fun getAll(): List<AuthorSubscription> = authors

        override suspend fun getById(id: Long): AuthorSubscription? = authors.find { it.id == id }

        override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? {
            return authors.find { it.normalizedQuery == normalizedQuery }
        }

        override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long = 0

        override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) = Unit

        override suspend fun replaceAll(subscriptions: List<AuthorSubscription>) = Unit

        override suspend fun getResultCaches(
            subscriptionIds: Collection<Long>,
        ): List<AuthorSubscriptionResultCache> = emptyList()

        override suspend fun upsertResultCache(subscriptionId: Long, mangaIds: List<Long>, cachedAt: Long) = Unit

        override suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>) {
            operations += "order:${updates.joinToString(",") { it.id.toString() }}"
            orderUpdates += updates
        }

        override suspend fun updatePinned(id: Long, pinned: Boolean) = Unit

        override suspend fun deleteById(id: Long) {
            operations += "delete:$id"
            deletedIds += id
        }

        override suspend fun deleteByNormalizedQuery(normalizedQuery: String) = Unit
    }

    companion object {

        @OptIn(DelicateCoroutinesApi::class)
        private val mainThreadSurrogate = newSingleThreadContext("UI thread")

        @BeforeAll
        @JvmStatic
        fun setUp() {
            Dispatchers.setMain(mainThreadSurrogate)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Dispatchers.resetMain()
            mainThreadSurrogate.close()
        }
    }
}
