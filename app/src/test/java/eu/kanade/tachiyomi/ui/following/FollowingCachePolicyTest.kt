package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FollowingCachePolicyTest {

    @Test
    fun `zero ttl disables automatic refresh for existing results`() {
        val policy = FollowingCachePolicy.fromPreference("0")

        policy.seedPersistentCache shouldBe false
        policy.shouldRefresh(lastRefreshAt = 100, hasSuccess = true, force = false, now = 100) shouldBe false
    }

    @Test
    fun `zero ttl loads subscriptions without a successful result`() {
        val policy = FollowingCachePolicy.fromPreference("0")

        policy.shouldRefresh(lastRefreshAt = null, hasSuccess = false, force = false, now = 100) shouldBe true
    }

    @Test
    fun `force refresh ignores zero ttl`() {
        val policy = FollowingCachePolicy.fromPreference("0")

        policy.shouldRefresh(lastRefreshAt = 100, hasSuccess = true, force = true, now = 100) shouldBe true
    }

    @Test
    fun `positive ttl skips successful fresh results`() {
        val policy = FollowingCachePolicy.fromPreference("24")

        policy.seedPersistentCache shouldBe true
        policy.shouldRefresh(lastRefreshAt = 100, hasSuccess = true, force = false, now = 200) shouldBe false
    }
}
