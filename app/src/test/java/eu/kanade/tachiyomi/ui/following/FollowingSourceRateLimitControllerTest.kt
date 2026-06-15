package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FollowingSourceRateLimitControllerTest {

    @Test
    fun `same source subscriptions share retry attempts`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        val rateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(10))
        controller.join(sourceId = 1, subscriptionIds = listOf(20, 30)) shouldBe FollowingSourceRateLimit(
            attempt = 1,
            max = 6,
            generation = rateLimit.generation,
        )

        repeat(5) { index ->
            controller.advanceAfterFailedRetry(sourceId = 1, generation = rateLimit.generation) shouldBe FollowingSourceRateLimit(
                attempt = index + 2,
                max = 6,
                generation = rateLimit.generation,
            )
        }

        controller.advanceAfterFailedRetry(sourceId = 1, generation = rateLimit.generation) shouldBe null
        controller.pendingSubscriptionIds(sourceId = 1) shouldContainExactly listOf(10L, 20L, 30L)
    }

    @Test
    fun `recover consumes source queue and returns pending subscriptions except completed one`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        val rateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(10, 20, 30))

        controller.consumeRecoveredPending(
            sourceId = 1,
            completedSubscriptionId = 20,
            generation = rateLimit.generation,
        ) shouldContainExactly listOf(10L, 30L)
        controller.join(sourceId = 1, subscriptionIds = listOf(40)) shouldBe null
    }

    @Test
    fun `stale retry generation cannot advance a reopened source`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        val oldRateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(10))
        controller.close(sourceId = 1)
        val newRateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(20))

        controller.advanceAfterFailedRetry(sourceId = 1, generation = oldRateLimit.generation) shouldBe null
        controller.current(sourceId = 1) shouldBe newRateLimit
        controller.advanceAfterFailedRetry(sourceId = 1, generation = newRateLimit.generation) shouldBe FollowingSourceRateLimit(
            attempt = 2,
            max = 6,
            generation = newRateLimit.generation,
        )
    }

    @Test
    fun `stale retry generation cannot recover a reopened source`() {
        val controller = FollowingSourceRateLimitController(maxAttempts = 6)

        val oldRateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(10))
        controller.close(sourceId = 1)
        val newRateLimit = controller.open(sourceId = 1, subscriptionIds = listOf(20, 30))

        controller.consumeRecoveredPending(sourceId = 1, completedSubscriptionId = 10, generation = oldRateLimit.generation) shouldBe null
        controller.current(sourceId = 1) shouldBe newRateLimit
        controller.pendingSubscriptionIds(sourceId = 1) shouldContainExactly listOf(20L, 30L)
    }
}
