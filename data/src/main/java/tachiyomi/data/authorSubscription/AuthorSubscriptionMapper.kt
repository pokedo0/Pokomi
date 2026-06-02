package tachiyomi.data.authorSubscription

import tachiyomi.domain.authorSubscription.model.AuthorSubscription

object AuthorSubscriptionMapper {
    fun map(
        id: Long,
        source: Long,
        name: String,
        query: String,
        normalizedQuery: String,
        createdAt: Long,
        updatedAt: Long,
        lastRefreshAt: Long?,
        sortOrder: Long,
        pinned: Long,
    ): AuthorSubscription {
        return AuthorSubscription(
            id = id,
            source = source,
            name = name,
            query = query,
            normalizedQuery = normalizedQuery,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastRefreshAt = lastRefreshAt,
            sortOrder = sortOrder,
            pinned = pinned != 0L,
        )
    }
}
