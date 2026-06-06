package tachiyomi.domain.authorSubscription.model

import tachiyomi.domain.manga.model.Manga

data class AuthorSubscriptionResultCache(
    val subscriptionId: Long,
    val mangas: List<Manga>,
    val cachedAt: Long,
)
