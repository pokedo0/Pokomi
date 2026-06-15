package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FollowingSerialRecoveryTest {

    @Test
    fun `serial recovery skips already completed success results`() {
        shouldSerialRecoveryProbe(FollowingItemResult.Success(emptyList(), refreshing = false)) shouldBe false
    }

    @Test
    fun `serial recovery probes unfinished or refreshing results`() {
        shouldSerialRecoveryProbe(null) shouldBe true
        shouldSerialRecoveryProbe(FollowingItemResult.Loading) shouldBe true
        shouldSerialRecoveryProbe(FollowingItemResult.RateLimited(sourceId = 1, attempt = 1, max = 6)) shouldBe true
        shouldSerialRecoveryProbe(FollowingItemResult.Success(emptyList(), refreshing = true)) shouldBe true
    }
}
