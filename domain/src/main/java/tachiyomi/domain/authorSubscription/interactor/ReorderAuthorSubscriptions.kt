package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class ReorderAuthorSubscriptions(
    private val repository: AuthorSubscriptionRepository,
) {

    suspend fun await(items: List<AuthorSubscription>) {
        repository.updateOrderIfChanged(
            current = items,
            updated = normalize(items),
        )
    }
}

fun normalize(items: List<AuthorSubscription>): List<AuthorSubscription> {
    return items
        .visualOrder()
        .mapIndexed { index, item -> item.copy(sortOrder = index.toLong()) }
}

fun reorder(
    items: List<AuthorSubscription>,
    fromIndex: Int,
    toIndex: Int,
): List<AuthorSubscription> {
    val visualItems = items.visualOrder().toMutableList()
    if (fromIndex !in visualItems.indices || toIndex !in visualItems.indices || fromIndex == toIndex) {
        return items
    }

    val item = visualItems.removeAt(fromIndex)
    val targetIndex = if (item.pinned) {
        toIndex.coerceIn(0, visualItems.firstUnpinnedIndex())
    } else {
        toIndex.coerceIn(visualItems.firstUnpinnedIndex(), visualItems.size)
    }

    visualItems.add(targetIndex, item)
    return normalize(visualItems)
}

fun moveToTop(
    items: List<AuthorSubscription>,
    id: Long,
): List<AuthorSubscription> {
    val visualItems = items.visualOrder().toMutableList()
    val currentIndex = visualItems.indexOfFirst { it.id == id }
    if (currentIndex == -1) {
        return items
    }

    val item = visualItems[currentIndex]
    val targetIndex = visualItems.indexOfFirst { it.pinned == item.pinned }.takeUnless { it == -1 } ?: visualItems.size
    if (currentIndex == targetIndex) {
        return items
    }

    visualItems.removeAt(currentIndex)
    visualItems.add(targetIndex, item)
    return normalize(visualItems)
}

fun moveToBottom(
    items: List<AuthorSubscription>,
    id: Long,
): List<AuthorSubscription> {
    val visualItems = items.visualOrder().toMutableList()
    val currentIndex = visualItems.indexOfFirst { it.id == id }
    if (currentIndex == -1) {
        return items
    }

    val item = visualItems[currentIndex]
    val targetIndex = if (item.pinned) {
        visualItems.firstUnpinnedIndex() - 1
    } else {
        visualItems.lastIndex
    }
    if (currentIndex == targetIndex) {
        return items
    }

    visualItems.removeAt(currentIndex)
    val insertionIndex = if (currentIndex < targetIndex) targetIndex else targetIndex + 1
    visualItems.add(insertionIndex, item)
    return normalize(visualItems)
}

fun togglePinned(
    items: List<AuthorSubscription>,
    id: Long,
): List<AuthorSubscription> {
    val visualItems = items.visualOrder().toMutableList()
    val item = visualItems.removeById(id) ?: return items
    val toggledItem = item.copy(pinned = !item.pinned)
    val targetIndex = visualItems.firstUnpinnedIndex()

    visualItems.add(targetIndex, toggledItem)
    return normalize(visualItems)
}

internal fun List<AuthorSubscription>.toOrderUpdates(): List<AuthorSubscriptionOrderUpdate> {
    return map {
        AuthorSubscriptionOrderUpdate(
            id = it.id,
            sortOrder = it.sortOrder,
            pinned = it.pinned,
        )
    }
}

internal suspend fun AuthorSubscriptionRepository.updateOrderIfChanged(
    current: List<AuthorSubscription>,
    updated: List<AuthorSubscription>,
) {
    val updates = updated.toOrderUpdates()
    if (current.toOrderUpdates() != updates) {
        updateOrder(updates)
    }
}

private fun List<AuthorSubscription>.visualOrder(): List<AuthorSubscription> {
    return filter { it.pinned } + filterNot { it.pinned }
}

private fun List<AuthorSubscription>.firstUnpinnedIndex(): Int {
    return indexOfFirst { !it.pinned }.takeUnless { it == -1 } ?: size
}

private fun MutableList<AuthorSubscription>.removeById(id: Long): AuthorSubscription? {
    val index = indexOfFirst { it.id == id }
    return if (index == -1) {
        null
    } else {
        removeAt(index)
    }
}
