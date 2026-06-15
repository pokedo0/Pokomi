package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAuthorSubscription
import eu.kanade.tachiyomi.data.backup.models.BackupFollowing
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AuthorSubscriptionRestorer(
    private val repository: AuthorSubscriptionRepository = Injekt.get(),
    private val followingPreferences: FollowingPreferences = Injekt.get(),
) {

    suspend fun restoreFollowing(backupFollowing: BackupFollowing) {
        val localLastModifiedAt = followingPreferences.lastModifiedAt().get()
        val shouldRestore = backupFollowing.lastModifiedAt > localLastModifiedAt ||
            (localLastModifiedAt == 0L && backupFollowing.subscriptions.isNotEmpty())

        if (!shouldRestore) return

        repository.replaceAll(
            backupFollowing.subscriptions
                .sortedWith(compareByDescending<BackupAuthorSubscription> { it.pinned }.thenBy { it.sortOrder })
                .mapIndexed { index, subscription ->
                    subscription.toAuthorSubscription(sortOrder = index.toLong())
                },
        )
        followingPreferences.lastModifiedAt().set(backupFollowing.lastModifiedAt)
    }
}

private fun BackupAuthorSubscription.toAuthorSubscription(sortOrder: Long): AuthorSubscription {
    return AuthorSubscription(
        id = 0,
        source = source,
        name = name,
        query = query,
        normalizedQuery = normalizedQuery.ifBlank { AuthorSubscription.normalizeQuery(query) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRefreshAt = lastRefreshAt,
        sortOrder = sortOrder,
        pinned = pinned,
    )
}
