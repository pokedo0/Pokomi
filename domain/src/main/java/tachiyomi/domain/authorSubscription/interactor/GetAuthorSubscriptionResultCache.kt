package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class GetAuthorSubscriptionResultCache(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(subscriptionIds: Collection<Long>): List<AuthorSubscriptionResultCache> {
        if (subscriptionIds.isEmpty()) return emptyList()

        return repository.getResultCaches(subscriptionIds)
    }
}
