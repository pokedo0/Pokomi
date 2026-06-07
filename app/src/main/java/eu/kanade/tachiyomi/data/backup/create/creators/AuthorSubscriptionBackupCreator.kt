package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupAuthorSubscription
import eu.kanade.tachiyomi.data.backup.models.BackupFollowing
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AuthorSubscriptionBackupCreator(
    private val getAuthorSubscriptions: GetAuthorSubscriptions = Injekt.get(),
    private val followingPreferences: FollowingPreferences = Injekt.get(),
) {

    suspend operator fun invoke(): BackupFollowing {
        return BackupFollowing(
            lastModifiedAt = followingPreferences.lastModifiedAt().get(),
            subscriptions = getAuthorSubscriptions.awaitAll().map { it.toBackupAuthorSubscription() },
        )
    }
}

private fun AuthorSubscription.toBackupAuthorSubscription(): BackupAuthorSubscription {
    return BackupAuthorSubscription(
        source = source,
        name = name,
        query = query,
        normalizedQuery = normalizedQuery,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRefreshAt = lastRefreshAt,
        sortOrder = sortOrder,
        pinned = pinned,
    )
}
