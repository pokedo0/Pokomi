package tachiyomi.domain.authorSubscription.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class GetAuthorSubscriptions(
    private val repository: AuthorSubscriptionRepository,
) {

    fun subscribeAll(): Flow<List<AuthorSubscription>> {
        return repository.subscribeAll()
    }

    suspend fun awaitAll(): List<AuthorSubscription> {
        return repository.getAll()
    }

    suspend fun awaitByQuery(query: String): AuthorSubscription? {
        return repository.getByNormalizedQuery(AuthorSubscription.normalizeQuery(query))
    }
}
