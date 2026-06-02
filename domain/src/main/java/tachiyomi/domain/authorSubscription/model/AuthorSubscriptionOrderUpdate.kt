package tachiyomi.domain.authorSubscription.model

data class AuthorSubscriptionOrderUpdate(
    val id: Long,
    val sortOrder: Long,
    val pinned: Boolean,
)
