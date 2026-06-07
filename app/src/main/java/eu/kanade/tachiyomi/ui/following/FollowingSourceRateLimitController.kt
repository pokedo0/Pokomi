package eu.kanade.tachiyomi.ui.following

internal class FollowingSourceRateLimitController(
    private val maxAttempts: Int,
) {

    private val rateLimits = mutableMapOf<Long, SourceRateLimitState>()
    private var nextGeneration = 1L

    @Synchronized
    fun open(sourceId: Long, subscriptionIds: Collection<Long>): FollowingSourceRateLimit {
        val state = rateLimits.getOrPut(sourceId) {
            SourceRateLimitState(
                rateLimit = FollowingSourceRateLimit(
                    attempt = 1,
                    max = maxAttempts,
                    generation = nextGeneration++,
                ),
            )
        }
        state.pendingSubscriptionIds += subscriptionIds
        return state.rateLimit
    }

    @Synchronized
    fun join(sourceId: Long, subscriptionIds: Collection<Long>): FollowingSourceRateLimit? {
        val state = rateLimits[sourceId] ?: return null
        state.pendingSubscriptionIds += subscriptionIds
        return state.rateLimit
    }

    @Synchronized
    fun addPending(sourceId: Long, subscriptionIds: Collection<Long>) {
        rateLimits[sourceId]?.pendingSubscriptionIds?.addAll(subscriptionIds)
    }

    @Synchronized
    fun current(sourceId: Long): FollowingSourceRateLimit? {
        return rateLimits[sourceId]?.rateLimit
    }

    @Synchronized
    fun markRetryRunning(sourceId: Long): Boolean {
        val state = rateLimits[sourceId] ?: return false
        if (state.retryRunning) return false
        state.retryRunning = true
        return true
    }

    @Synchronized
    fun advanceAfterFailedRetry(sourceId: Long, generation: Long): FollowingSourceRateLimit? {
        val state = rateLimits[sourceId] ?: return null
        if (state.rateLimit.generation != generation) return null

        val nextAttempt = state.rateLimit.attempt + 1
        if (nextAttempt > maxAttempts) return null

        val next = state.rateLimit.copy(attempt = nextAttempt)
        state.rateLimit = next
        return next
    }

    @Synchronized
    fun consumeRecoveredPending(sourceId: Long, completedSubscriptionId: Long, generation: Long): List<Long>? {
        val state = rateLimits.remove(sourceId) ?: return emptyList()
        if (state.rateLimit.generation != generation) {
            rateLimits[sourceId] = state
            return null
        }
        state.pendingSubscriptionIds -= completedSubscriptionId
        return state.pendingSubscriptionIds.toList()
    }

    @Synchronized
    fun close(sourceId: Long) {
        rateLimits.remove(sourceId)
    }

    @Synchronized
    fun pendingSubscriptionIds(sourceId: Long): List<Long> {
        return rateLimits[sourceId]?.pendingSubscriptionIds?.toList().orEmpty()
    }

    private data class SourceRateLimitState(
        var rateLimit: FollowingSourceRateLimit,
        val pendingSubscriptionIds: LinkedHashSet<Long> = linkedSetOf(),
        var retryRunning: Boolean = false,
    )
}
