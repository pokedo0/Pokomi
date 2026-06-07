package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences

class DeleteAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
    private val preferences: FollowingPreferences,
) {

    suspend fun awaitById(id: Long) {
        repository.deleteById(id)
        preferences.lastModifiedAt().set(System.currentTimeMillis())
    }

    suspend fun awaitByQuery(query: String) {
        repository.deleteByNormalizedQuery(AuthorSubscription.normalizeQuery(query))
        preferences.lastModifiedAt().set(System.currentTimeMillis())
    }
}
