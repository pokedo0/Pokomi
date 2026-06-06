package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Immutable
import tachiyomi.domain.manga.model.Manga

@Immutable
sealed interface FollowingItemResult {
    data object Loading : FollowingItemResult

    data class RateLimited(
        val attempt: Int,
        val max: Int,
    ) : FollowingItemResult

    data object Stalled : FollowingItemResult

    data class Error(
        val throwable: Throwable,
    ) : FollowingItemResult

    data class Success(
        val result: List<Manga>,
    ) : FollowingItemResult
}
