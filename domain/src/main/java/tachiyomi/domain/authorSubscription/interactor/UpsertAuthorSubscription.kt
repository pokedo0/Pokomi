package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class UpsertAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(source: Long, query: String, name: String = query.trim()): Long {
        val normalizedQuery = AuthorSubscription.normalizeQuery(query)
        require(normalizedQuery.isNotBlank()) { "Author subscription query must not be blank" }

        return repository.upsert(
            source = source,
            name = name.trim().ifBlank { query.trim() },
            query = query.trim(),
            normalizedQuery = normalizedQuery,
        )
    }
}
