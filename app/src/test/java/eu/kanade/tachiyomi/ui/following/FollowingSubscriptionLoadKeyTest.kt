package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.authorSubscription.model.AuthorSubscription

class FollowingSubscriptionLoadKeyTest {

    @Test
    fun `refresh timestamps do not change load keys`() {
        val subscription = authorSubscription()

        listOf(subscription.copy(lastRefreshAt = 20, updatedAt = 30))
            .toFollowingSubscriptionLoadKeys() shouldBe listOf(subscription).toFollowingSubscriptionLoadKeys()
    }

    @Test
    fun `query source and order changes change load keys`() {
        val subscription = authorSubscription()
        val originalKey = listOf(subscription).toFollowingSubscriptionLoadKeys()

        listOf(subscription.copy(source = 2)).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
        listOf(subscription.copy(query = "other")).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
        listOf(subscription.copy(normalizedQuery = "other")).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
        listOf(subscription.copy(name = "Other")).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
        listOf(subscription.copy(sortOrder = 2)).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
        listOf(subscription.copy(pinned = true)).toFollowingSubscriptionLoadKeys() shouldNotBe originalKey
    }

    private fun authorSubscription(): AuthorSubscription {
        return AuthorSubscription(
            id = 1,
            source = 1,
            name = "Author",
            query = "author",
            normalizedQuery = "author",
            createdAt = 1,
            updatedAt = 1,
            lastRefreshAt = 1,
            sortOrder = 1,
            pinned = false,
        )
    }
}
