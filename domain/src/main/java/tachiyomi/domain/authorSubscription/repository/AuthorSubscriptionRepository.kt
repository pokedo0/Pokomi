package tachiyomi.domain.authorSubscription.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate

interface AuthorSubscriptionRepository {

    fun subscribeAll(): Flow<List<AuthorSubscription>>

    suspend fun getAll(): List<AuthorSubscription>

    suspend fun getById(id: Long): AuthorSubscription?

    suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription?

    suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long

    suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long)

    suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>)

    suspend fun updatePinned(id: Long, pinned: Boolean)

    suspend fun deleteById(id: Long)

    suspend fun deleteByNormalizedQuery(normalizedQuery: String)
}
