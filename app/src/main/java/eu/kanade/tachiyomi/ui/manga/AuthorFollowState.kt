package eu.kanade.tachiyomi.ui.manga

import tachiyomi.domain.authorSubscription.model.AuthorSubscription

data class AuthorFollowState(
    val name: String,
    val followed: Boolean,
)

fun buildAuthorFollowStates(
    sourceId: Long,
    author: String?,
    artist: String?,
    subscriptions: List<AuthorSubscription>,
): List<AuthorFollowState> {
    val followedQueries = subscriptions
        .filter { it.source == sourceId }
        .map { it.normalizedQuery }
        .toSet()

    return listOfNotNull(author.cleanAuthorName(), artist.cleanAuthorName())
        .distinctBy { AuthorSubscription.normalizeQuery(it) }
        .map { name ->
            AuthorFollowState(
                name = name,
                followed = AuthorSubscription.normalizeQuery(name) in followedQueries,
            )
        }
}

fun findAuthorFollowConflict(
    sourceId: Long,
    name: String,
    subscriptions: List<AuthorSubscription>,
): AuthorSubscription? {
    val normalizedName = AuthorSubscription.normalizeQuery(name)
    return subscriptions.firstOrNull {
        it.source != sourceId && it.normalizedQuery == normalizedName
    }
}

private fun String?.cleanAuthorName(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}
