package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class DeleteAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun awaitById(id: Long) {
        repository.deleteById(id)
    }

    suspend fun awaitByQuery(query: String) {
        repository.deleteByNormalizedQuery(AuthorSubscription.normalizeQuery(query))
    }
}
