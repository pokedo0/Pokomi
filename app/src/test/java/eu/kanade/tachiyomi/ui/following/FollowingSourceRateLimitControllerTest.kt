package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FollowingSourceRateLimitControllerTest {

    @Test
    fun `same source subscriptions share retry attempts`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        controller.open(sourceId = 1, subscriptionIds = listOf(10))
        controller.join(sourceId = 1, subscriptionIds = listOf(20, 30)) shouldBe FollowingSourceRateLimit(
            attempt = 1,
            max = 6,
        )

        repeat(5) { index ->
            controller.advanceAfterFailedRetry(sourceId = 1) shouldBe FollowingSourceRateLimit(
                attempt = index + 2,
                max = 6,
            )
        }

        controller.advanceAfterFailedRetry(sourceId = 1) shouldBe null
        controller.pendingSubscriptionIds(sourceId = 1) shouldContainExactly listOf(10L, 20L, 30L)
    }

    @Test
    fun `recover clears source queue and returns pending subscriptions except completed one`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        controller.open(sourceId = 1, subscriptionIds = listOf(10, 20, 30))

        controller.recover(sourceId = 1, completedSubscriptionId = 20) shouldContainExactly listOf(10L, 30L)
        controller.join(sourceId = 1, subscriptionIds = listOf(40)) shouldBe null
    }
}
