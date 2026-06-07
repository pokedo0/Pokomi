package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences

class UpsertAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
    private val preferences: FollowingPreferences,
) {

    suspend fun await(source: Long, query: String, name: String = query.trim()): Long {
        val normalizedQuery = AuthorSubscription.normalizeQuery(query)
        require(normalizedQuery.isNotBlank()) { "Author subscription query must not be blank" }

        return repository.upsert(
            source = source,
            name = name.trim().ifBlank { query.trim() },
            query = query.trim(),
            normalizedQuery = normalizedQuery,
        ).also {
            preferences.lastModifiedAt().set(System.currentTimeMillis())
        }
    }
}
