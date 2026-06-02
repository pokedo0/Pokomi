package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class MoveAuthorSubscriptionToTop(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(id: Long, items: List<AuthorSubscription>) {
        repository.updateOrderIfChanged(
            current = items,
            updated = moveToTop(items, id),
        )
    }
}
