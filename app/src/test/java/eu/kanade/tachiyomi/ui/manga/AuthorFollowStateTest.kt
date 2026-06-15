package eu.kanade.tachiyomi.ui.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.authorSubscription.model.AuthorSubscription

class AuthorFollowStateTest {

    @Test
    fun `builds separate author and artist follow states`() {
        val states = buildAuthorFollowStates(
            sourceId = 2,
            author = "  佐伯 凪  ",
            artist = "キョウ屋斎",
            subscriptions = listOf(subscription(name = "佐伯 凪")),
        )

        states shouldBe listOf(
            AuthorFollowState(name = "佐伯 凪", followed = true),
            AuthorFollowState(name = "キョウ屋斎", followed = false),
        )
    }

    @Test
    fun `omits duplicate artist`() {
        val states = buildAuthorFollowStates(
            sourceId = 2,
            author = "ONE",
            artist = " one ",
            subscriptions = listOf(subscription(name = "ONE")),
        )

        states shouldBe listOf(
            AuthorFollowState(name = "ONE", followed = true),
        )
    }

    @Test
    fun `builds unfollowed states when author details load later`() {
        buildAuthorFollowStates(
            sourceId = 2,
            author = null,
            artist = null,
            subscriptions = emptyList(),
        ) shouldBe emptyList()

        val states = buildAuthorFollowStates(
            sourceId = 2,
            author = "great mosu",
            artist = "mosquitone.",
            subscriptions = emptyList(),
        )

        states shouldBe listOf(
            AuthorFollowState(name = "great mosu", followed = false),
            AuthorFollowState(name = "mosquitone.", followed = false),
        )
    }

    @Test
    fun `does not mark same author followed from another source`() {
        val states = buildAuthorFollowStates(
            sourceId = 20,
            author = "Nosebleed",
            artist = "Miyamoto Issa",
            subscriptions = listOf(subscription(source = 10, name = "miyamoto issa")),
        )

        states shouldBe listOf(
            AuthorFollowState(name = "Nosebleed", followed = false),
            AuthorFollowState(name = "Miyamoto Issa", followed = false),
        )
    }

    @Test
    fun `finds same author subscription from another source`() {
        findAuthorFollowConflict(
            sourceId = 20,
            name = "Miyamoto Issa",
            subscriptions = listOf(subscription(source = 10, name = "miyamoto issa")),
        ) shouldBe subscription(source = 10, name = "miyamoto issa")
    }

    @Test
    fun `does not conflict with same author subscription from current source`() {
        findAuthorFollowConflict(
            sourceId = 20,
            name = "Miyamoto Issa",
            subscriptions = listOf(subscription(source = 20, name = "miyamoto issa")),
        ) shouldBe null
    }

    private fun subscription(source: Long = 2, name: String): AuthorSubscription {
        return AuthorSubscription(
            id = 1,
            source = source,
            name = name,
            query = name,
            normalizedQuery = AuthorSubscription.normalizeQuery(name),
            createdAt = 0,
            updatedAt = 0,
            lastRefreshAt = null,
            sortOrder = 0,
            pinned = false,
        )
    }
}
