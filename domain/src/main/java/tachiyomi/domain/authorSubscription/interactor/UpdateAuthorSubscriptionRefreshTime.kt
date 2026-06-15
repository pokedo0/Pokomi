package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class UpdateAuthorSubscriptionRefreshTime(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(id: Long, lastRefreshAt: Long = System.currentTimeMillis()) {
        repository.updateLastRefreshAt(id, lastRefreshAt)
    }
}
