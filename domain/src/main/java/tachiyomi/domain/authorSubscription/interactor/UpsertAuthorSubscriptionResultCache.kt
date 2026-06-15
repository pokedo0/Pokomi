package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.manga.model.Manga

class UpsertAuthorSubscriptionResultCache(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(
        subscriptionId: Long,
        mangas: List<Manga>,
        cachedAt: Long = System.currentTimeMillis(),
    ) {
        repository.upsertResultCache(subscriptionId, mangas.map { it.id }, cachedAt)
    }
}
