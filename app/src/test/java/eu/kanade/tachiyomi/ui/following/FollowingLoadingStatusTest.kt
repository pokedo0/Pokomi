package eu.kanade.tachiyomi.ui.following

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.source.NoResultsException
import java.io.IOException

class FollowingLoadingStatusTest {

    @Test
    fun `begin uses total author count and existing successful author count`() {
        val status = FollowingLoadingStatus()

        status.begin(total = 100, successful = 10)

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 10,
            total = 100,
        )
    }

    @Test
    fun `successful load increases successful count`() {
        val status = FollowingLoadingStatus()
        status.begin(total = 100, successful = 10)

        status.markSuccessful(1)

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 11,
            total = 100,
        )
    }

    @Test
    fun `failed load completes without increasing successful count`() {
        val status = FollowingLoadingStatus()
        status.begin(total = 100, successful = 10)

        status.markFinishedWithoutSuccess()

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 10,
            total = 100,
        )
    }

    @Test
    fun `failed active load keeps banner visible until load finishes`() {
        val status = FollowingLoadingStatus()
        status.startLoad(total = 100, successful = 10, loadingIds = listOf(42))

        status.markFinishedWithoutSuccess(42)

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 10,
            total = 100,
        )

        status.finish()

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = false,
            successful = 10,
            total = 100,
        )
    }

    @Test
    fun `finish hides banner and keeps final progress`() {
        val status = FollowingLoadingStatus()
        status.begin(total = 100, successful = 10)
        status.markSuccessful(1)

        status.finish()

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = false,
            successful = 11,
            total = 100,
        )
    }

    @Test
    fun `fast load waits for minimum visible duration before hiding banner`() {
        followingBannerFinishDelayMillis(
            startedAt = 1_000,
            now = 1_100,
            minVisibleMillis = 600,
        ) shouldBe 500

        followingBannerFinishDelayMillis(
            startedAt = 1_000,
            now = 1_700,
            minVisibleMillis = 600,
        ) shouldBe 0
    }

    @Test
    fun `completed check briefly shows a finished full progress banner`() {
        val status = FollowingLoadingStatus()

        status.showCompletedCheck(total = 45, successful = 45)

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 45,
            total = 45,
        )

        status.finishCompletedCheck()

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = false,
            successful = 45,
            total = 45,
        )
    }

    @Test
    fun `completed check does not override an active load`() {
        val status = FollowingLoadingStatus()
        status.startLoad(total = 31, successful = 28, loadingIds = listOf(29, 30, 31))

        status.showCompletedCheck(total = 31, successful = 31)

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 28,
            total = 31,
        )

        status.finishCompletedCheck()

        status.snapshot shouldBe FollowingLoadingSnapshot(
            running = true,
            successful = 28,
            total = 31,
        )
    }

    @Test
    fun `begin with no authors stays hidden`() {
        val status = FollowingLoadingStatus()

        status.begin(total = 0, successful = 0)

        status.snapshot shouldBe FollowingLoadingSnapshot()
    }

    @Test
    fun `overlapping loads keep banner visible until all finish`() {
        val status = FollowingLoadingStatus()
        status.startLoad(total = 100, successful = 9, loadingIds = listOf(1))
        status.startLoad(total = 100, successful = 8, loadingIds = listOf(2))

        status.snapshot.successful shouldBe 8

        status.finish()

        status.snapshot.running shouldBe true

        status.finish()

        status.snapshot.running shouldBe false
    }

    @Test
    fun `same author success only increases count once`() {
        val status = FollowingLoadingStatus()
        status.startLoad(total = 100, successful = 9, loadingIds = listOf(1))

        status.markSuccessful(1)
        status.markSuccessful(1)

        status.snapshot.successful shouldBe 10
    }

    @Test
    fun `loading existing successful authors removes them from successful count until they succeed again`() {
        val status = FollowingLoadingStatus()

        status.startLoad(total = 100, successful = 8, loadingIds = listOf(1, 2))

        status.snapshot.successful shouldBe 8

        status.markSuccessful(1)

        status.snapshot.successful shouldBe 9
    }

    @Test
    fun `starting retry for already pending authors does not reduce successful count again`() {
        val status = FollowingLoadingStatus()
        status.startLoad(total = 100, successful = 8, loadingIds = listOf(1, 2))
        status.markSuccessful(1)
        status.finish()

        status.startLoad(total = 100, successful = 9, loadingIds = listOf(2))

        status.snapshot.successful shouldBe 9
    }

    @Test
    fun `no results counts as successful empty load`() {
        isFollowingNoResultsSuccess(NoResultsException()) shouldBe true
    }

    @Test
    fun `other errors do not count as successful empty load`() {
        isFollowingNoResultsSuccess(IOException("network")) shouldBe false
    }

    @Test
    fun `in flight results should not start another load`() {
        isFollowingLoadInFlight(FollowingItemResult.Loading) shouldBe true
        isFollowingLoadInFlight(
            FollowingItemResult.RateLimited(
                sourceId = 1,
                attempt = 1,
                max = 6,
            ),
        ) shouldBe true
        isFollowingLoadInFlight(FollowingItemResult.Success(emptyList(), refreshing = true)) shouldBe true
    }

    @Test
    fun `completed results can start another load`() {
        isFollowingLoadInFlight(null) shouldBe false
        isFollowingLoadInFlight(FollowingItemResult.Success(emptyList())) shouldBe false
        isFollowingLoadInFlight(FollowingItemResult.Error(IOException("network"))) shouldBe false
        isFollowingLoadInFlight(FollowingItemResult.Stalled) shouldBe false
    }
}
