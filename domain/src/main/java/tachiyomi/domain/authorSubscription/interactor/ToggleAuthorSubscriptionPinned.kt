package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences

class ToggleAuthorSubscriptionPinned(
    private val repository: AuthorSubscriptionRepository,
    private val preferences: FollowingPreferences,
) {

    suspend fun await(id: Long, items: List<AuthorSubscription>) {
        repository.updateOrderIfChanged(
            current = items,
            updated = togglePinned(items, id),
            onChanged = { preferences.lastModifiedAt().set(System.currentTimeMillis()) },
        )
    }
}
