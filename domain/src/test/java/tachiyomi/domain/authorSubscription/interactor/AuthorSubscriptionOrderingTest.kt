package tachiyomi.domain.authorSubscription.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class AuthorSubscriptionOrderingTest {

    @Test
    fun `normalize keeps pinned authors before unpinned authors`() {
        val result = normalize(
            listOf(
                author(id = 1, pinned = false, sortOrder = 9),
                author(id = 2, pinned = true, sortOrder = 4),
                author(id = 3, pinned = false, sortOrder = 2),
                author(id = 4, pinned = true, sortOrder = 1),
            ),
        )

        result.map { it.id } shouldBe listOf(2, 4, 1, 3)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `reorder changes visual order and normalizes sort order`() {
        val result = reorder(
            items = listOf(
                author(id = 1, sortOrder = 10),
                author(id = 2, sortOrder = 20),
                author(id = 3, sortOrder = 30),
            ),
            fromIndex = 2,
            toIndex = 0,
        )

        result.map { it.id } shouldBe listOf(3, 1, 2)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L)
        result.map { it.pinned } shouldBe listOf(false, false, false)
    }

    @Test
    fun `reorder pinned author below pinned block places it at bottom of pinned block`() {
        val items = listOf(
            author(id = 1, pinned = true),
            author(id = 2, pinned = true),
            author(id = 3),
            author(id = 4),
        )

        val result = reorder(items = items, fromIndex = 0, toIndex = 3)

        result.map { it.id } shouldBe listOf(2, 1, 3, 4)
        result.map { it.pinned } shouldBe listOf(true, true, false, false)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `reorder unpinned author above unpinned block places it at top of unpinned block`() {
        val items = listOf(
            author(id = 1, pinned = true),
            author(id = 2, pinned = true),
            author(id = 3),
            author(id = 4),
        )

        val result = reorder(items = items, fromIndex = 3, toIndex = 0)

        result.map { it.id } shouldBe listOf(1, 2, 4, 3)
        result.map { it.pinned } shouldBe listOf(true, true, false, false)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `reorder with invalid indices returns items unchanged`() {
        val items = listOf(
            author(id = 1, sortOrder = 10),
            author(id = 2, sortOrder = 20),
        )

        reorder(items = items, fromIndex = -1, toIndex = 1) shouldBe items
        reorder(items = items, fromIndex = 0, toIndex = 2) shouldBe items
    }

    @Test
    fun `move to top keeps author in its pinned block`() {
        val result = moveToTop(
            items = listOf(
                author(id = 1, pinned = true),
                author(id = 2, pinned = true),
                author(id = 3),
                author(id = 4),
            ),
            id = 4,
        )

        result.map { it.id } shouldBe listOf(1, 2, 4, 3)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `move to top with missing id returns items unchanged`() {
        val items = listOf(
            author(id = 1, sortOrder = 10),
            author(id = 2, sortOrder = 20),
        )

        moveToTop(items = items, id = 3) shouldBe items
    }

    @Test
    fun `move to top for already top author returns items unchanged`() {
        val items = listOf(
            author(id = 1, pinned = true),
            author(id = 2, pinned = true),
            author(id = 3),
        )

        moveToTop(items = items, id = 1) shouldBe items
        moveToTop(items = items, id = 3) shouldBe items
    }

    @Test
    fun `move to bottom keeps author in its pinned block`() {
        val result = moveToBottom(
            items = listOf(
                author(id = 1, pinned = true),
                author(id = 2, pinned = true),
                author(id = 3),
                author(id = 4),
            ),
            id = 1,
        )

        result.map { it.id } shouldBe listOf(2, 1, 3, 4)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `move to bottom with missing id returns items unchanged`() {
        val items = listOf(
            author(id = 1, sortOrder = 10),
            author(id = 2, sortOrder = 20),
        )

        moveToBottom(items = items, id = 3) shouldBe items
    }

    @Test
    fun `move to bottom for already bottom author returns items unchanged`() {
        val items = listOf(
            author(id = 1, pinned = true),
            author(id = 2, pinned = true),
            author(id = 3),
            author(id = 4),
        )

        moveToBottom(items = items, id = 2) shouldBe items
        moveToBottom(items = items, id = 4) shouldBe items
    }

    @Test
    fun `toggle pin moves newly pinned author to end of pinned block`() {
        val result = togglePinned(
            items = listOf(
                author(id = 1, pinned = true),
                author(id = 2, pinned = true),
                author(id = 3),
                author(id = 4),
            ),
            id = 3,
        )

        result.map { it.id } shouldBe listOf(1, 2, 3, 4)
        result.map { it.pinned } shouldBe listOf(true, true, true, false)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `toggle pin moves newly unpinned author to top of unpinned block`() {
        val result = togglePinned(
            items = listOf(
                author(id = 1, pinned = true),
                author(id = 2, pinned = true),
                author(id = 3),
                author(id = 4),
            ),
            id = 2,
        )

        result.map { it.id } shouldBe listOf(1, 2, 3, 4)
        result.map { it.pinned } shouldBe listOf(true, false, false, false)
        result.map { it.sortOrder } shouldBe listOf(0L, 1L, 2L, 3L)
    }

    @Test
    fun `toggle pin with missing id returns items unchanged`() {
        val items = listOf(
            author(id = 1, sortOrder = 10),
            author(id = 2, sortOrder = 20),
        )

        togglePinned(items = items, id = 3) shouldBe items
    }

    @Test
    fun `reorder interactor skips repository write when items are already normalized`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val items = listOf(
            author(id = 1, sortOrder = 0),
            author(id = 2, sortOrder = 1),
        )

        ReorderAuthorSubscriptions(repository).await(items)

        repository.orderUpdates shouldBe emptyList()
    }

    @Test
    fun `move to top interactor skips repository write when id is missing`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val items = listOf(
            author(id = 1, sortOrder = 0),
            author(id = 2, sortOrder = 1),
        )

        MoveAuthorSubscriptionToTop(repository).await(id = 3, items = items)

        repository.orderUpdates shouldBe emptyList()
    }

    @Test
    fun `move to top interactor skips repository write when author is already at top of block`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val items = listOf(
            author(id = 1, pinned = true, sortOrder = 0),
            author(id = 2, pinned = true, sortOrder = 1),
            author(id = 3, sortOrder = 2),
        )

        MoveAuthorSubscriptionToTop(repository).await(id = 1, items = items)

        repository.orderUpdates shouldBe emptyList()
    }

    @Test
    fun `move to bottom interactor skips repository write when author is already at bottom of block`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val items = listOf(
            author(id = 1, pinned = true, sortOrder = 0),
            author(id = 2, pinned = true, sortOrder = 1),
            author(id = 3, sortOrder = 2),
        )

        MoveAuthorSubscriptionToBottom(repository).await(id = 2, items = items)

        repository.orderUpdates shouldBe emptyList()
    }

    @Test
    fun `toggle pin interactor skips repository write when id is missing`() = runTest {
        val repository = FakeAuthorSubscriptionRepository()
        val items = listOf(
            author(id = 1, sortOrder = 0),
            author(id = 2, sortOrder = 1),
        )

        ToggleAuthorSubscriptionPinned(repository).await(id = 3, items = items)

        repository.orderUpdates shouldBe emptyList()
    }

    private fun author(
        id: Long,
        pinned: Boolean = false,
        sortOrder: Long = id,
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

    private class FakeAuthorSubscriptionRepository : AuthorSubscriptionRepository {

        val orderUpdates = mutableListOf<List<AuthorSubscriptionOrderUpdate>>()

        override fun subscribeAll(): Flow<List<AuthorSubscription>> = emptyFlow()

        override suspend fun getAll(): List<AuthorSubscription> = emptyList()

        override suspend fun getById(id: Long): AuthorSubscription? = null

        override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? = null

        override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long = 0

        override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) = Unit

        override suspend fun getResultCaches(
            subscriptionIds: Collection<Long>,
        ): List<AuthorSubscriptionResultCache> = emptyList()

        override suspend fun upsertResultCache(subscriptionId: Long, mangaIds: List<Long>, cachedAt: Long) = Unit

        override suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>) {
            orderUpdates += updates
        }

        override suspend fun updatePinned(id: Long, pinned: Boolean) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteByNormalizedQuery(normalizedQuery: String) = Unit
    }
}
