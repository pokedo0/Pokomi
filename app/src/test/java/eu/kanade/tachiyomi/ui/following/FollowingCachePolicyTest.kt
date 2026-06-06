package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FollowingCachePolicyTest {

    @Test
    fun `zero ttl disables cache seeding and forces refresh`() {
        val policy = FollowingCachePolicy.fromPreference("0")

        policy.seedPersistentCache shouldBe false
        policy.shouldRefresh(lastRefreshAt = 100, hasSuccess = true, force = false, now = 100) shouldBe true
    }

    @Test
    fun `positive ttl skips successful fresh results`() {
        val policy = FollowingCachePolicy.fromPreference("24")

        policy.seedPersistentCache shouldBe true
        policy.shouldRefresh(lastRefreshAt = 100, hasSuccess = true, force = false, now = 200) shouldBe false
    }
}
