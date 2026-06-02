package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.ReorderAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.moveToBottom
import tachiyomi.domain.authorSubscription.interactor.moveToTop
import tachiyomi.domain.authorSubscription.interactor.reorder
import tachiyomi.domain.authorSubscription.interactor.togglePinned
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AuthorRankScreenModel(
    initialAuthorId: Long?,
    private val getAuthorSubscriptions: GetAuthorSubscriptions = Injekt.get(),
    private val reorderAuthorSubscriptions: ReorderAuthorSubscriptions = Injekt.get(),
) : StateScreenModel<AuthorRankScreenModel.State>(
    State(
        initialAuthorId = initialAuthorId,
        highlightedAuthorId = initialAuthorId,
    ),
) {

    init {
        screenModelScope.launchIO {
            val subscriptions = getAuthorSubscriptions.awaitAll()
            mutableState.update {
                it.copy(
                    items = subscriptions,
                    initialSnapshot = subscriptions.toSnapshot(),
                )
            }
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        mutableState.update {
            if (it.saving) return@update it

            it.copy(
                items = reorder(it.items, fromIndex, toIndex),
                error = null,
            )
        }
    }

    fun moveToTop(id: Long) {
        mutableState.update {
            if (it.saving) return@update it

            it.copy(
                items = moveToTop(it.items, id),
                error = null,
            )
        }
    }

    fun moveToBottom(id: Long) {
        mutableState.update {
            if (it.saving) return@update it

            it.copy(
                items = moveToBottom(it.items, id),
                error = null,
            )
        }
    }

    fun togglePinned(id: Long) {
        mutableState.update {
            if (it.saving) return@update it

            it.copy(
                items = togglePinned(it.items, id),
                error = null,
            )
        }
    }

    fun save(
        onSaved: (Long?) -> Unit,
        navigateUp: () -> Unit,
    ) {
        val state = state.value
        if (state.saving) return
        if (!state.hasChanges) {
            navigateUp()
            return
        }

        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                withIOContext {
                    reorderAuthorSubscriptions.await(state.items.forSave())
                }
                onSaved(state.highlightedAuthorId)
                navigateUp()
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        saving = false,
                        error = e.message.orEmpty(),
                    )
                }
            }
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    @Immutable
    data class State(
        val items: List<AuthorSubscription> = emptyList(),
        val initialSnapshot: List<AuthorRankSnapshotItem> = emptyList(),
        val initialAuthorId: Long? = null,
        val highlightedAuthorId: Long? = null,
        val saving: Boolean = false,
        val error: String? = null,
    ) {
        val hasChanges: Boolean
            get() = items.toSnapshot() != initialSnapshot
    }
}

@Immutable
data class AuthorRankSnapshotItem(
    val id: Long,
    val sortOrder: Long,
    val pinned: Boolean,
)

private fun List<AuthorSubscription>.toSnapshot(): List<AuthorRankSnapshotItem> {
    return map {
        AuthorRankSnapshotItem(
            id = it.id,
            sortOrder = it.sortOrder,
            pinned = it.pinned,
        )
    }
}

private fun List<AuthorSubscription>.forSave(): List<AuthorSubscription> {
    return map { it.copy(sortOrder = UNSAVED_SORT_ORDER) }
}

private const val UNSAVED_SORT_ORDER = Long.MIN_VALUE
