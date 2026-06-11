package eu.kanade.tachiyomi.ui.following

import kotlinx.coroutines.flow.MutableStateFlow

data class FollowingLoadingSnapshot(
    val running: Boolean = false,
    val successful: Int = 0,
    val total: Int = 0,
) {
    val progress: Float?
        get() = total.takeIf { it > 0 }?.let { successful.toFloat() / it }
}

class FollowingLoadingStatus {

    val state = MutableStateFlow(FollowingLoadingSnapshot())
    private var activeLoads = 0
    private val successfulLoadIds = mutableSetOf<Long>()

    val snapshot: FollowingLoadingSnapshot
        get() = state.value

    val hasActiveLoads: Boolean
        get() = activeLoads > 0

    fun startLoad(total: Int, successful: Int, loadingIds: Collection<Long>) {
        activeLoads++
        successfulLoadIds.removeAll(loadingIds.toSet())
        if (activeLoads == 1) {
            successfulLoadIds.clear()
            begin(total, successful)
        } else {
            state.value = state.value.copy(
                running = total > 0,
                successful = minOf(state.value.successful, successful).coerceIn(0, total),
                total = total,
            )
        }
    }

    fun begin(total: Int, successful: Int) {
        state.value = if (total > 0) {
            FollowingLoadingSnapshot(
                running = true,
                successful = successful.coerceIn(0, total),
                total = total,
            )
        } else {
            FollowingLoadingSnapshot()
        }
    }

    fun markSuccessful(id: Long) {
        if (!successfulLoadIds.add(id)) return

        state.value = state.value.let {
            it.copy(successful = (it.successful + 1).coerceAtMost(it.total))
        }
    }

    fun updateSuccessful(successful: Int) {
        state.value = state.value.let {
            it.copy(successful = successful.coerceIn(0, it.total))
        }
    }

    fun markFinishedWithoutSuccess(id: Long? = null) {
        state.value = state.value
    }

    fun showCompletedCheck(total: Int, successful: Int) {
        if (activeLoads > 0) return

        state.value = if (total > 0) {
            FollowingLoadingSnapshot(
                running = true,
                successful = successful.coerceIn(0, total),
                total = total,
            )
        } else {
            FollowingLoadingSnapshot()
        }
    }

    fun finishCompletedCheck() {
        if (activeLoads > 0) return

        state.value = state.value.copy(running = false)
    }

    fun finish() {
        activeLoads = (activeLoads - 1).coerceAtLeast(0)
        if (activeLoads > 0) return

        successfulLoadIds.clear()
        state.value = state.value.copy(running = false)
    }

    fun reset() {
        activeLoads = 0
        successfulLoadIds.clear()
        state.value = FollowingLoadingSnapshot()
    }
}

internal fun followingBannerFinishDelayMillis(
    startedAt: Long,
    now: Long,
    minVisibleMillis: Long,
): Long {
    return (minVisibleMillis - (now - startedAt)).coerceAtLeast(0)
}
