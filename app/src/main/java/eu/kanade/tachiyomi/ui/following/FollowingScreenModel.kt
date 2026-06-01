package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpdateAuthorSubscriptionRefreshTime
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors
import androidx.compose.runtime.State as ComposeState

class FollowingScreenModel(
    private val getAuthorSubscriptions: GetAuthorSubscriptions = Injekt.get(),
    private val updateAuthorSubscriptionRefreshTime: UpdateAuthorSubscriptionRefreshTime = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<FollowingScreenModel.State>(State()) {

    private val searchDispatcher = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS).asCoroutineDispatcher()
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    init {
        screenModelScope.launchIO {
            getAuthorSubscriptions.subscribeAll().collectLatest { subscriptions ->
                mutableState.update { current ->
                    current.copy(
                        subscriptions = subscriptions,
                        results = current.results
                            .filterKeys { id -> subscriptions.any { it.id == id } }
                            .toPersistentMap(),
                    )
                }
                loadInitial()
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): ComposeState<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga -> value = manga }
        }
    }

    fun loadInitial() {
        load(state.value.subscriptions.take(INITIAL_LOAD_COUNT).map { it.id })
    }

    fun loadVisible(subscriptionId: Long) {
        load(listOf(subscriptionId))
    }

    fun refresh(subscriptionId: Long) {
        refresh(listOf(subscriptionId))
    }

    fun refreshLoaded() {
        val loadedIds = state.value.results.keys
        val refreshIds = loadedIds.ifEmpty {
            state.value.subscriptions.take(INITIAL_LOAD_COUNT).map { it.id }
        }
        refresh(refreshIds)
    }

    private fun refresh(subscriptionIds: Collection<Long>) {
        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    subscriptionIds.forEach { subscriptionId ->
                        results[subscriptionId] = SearchItemResult.Loading
                    }
                },
            )
        }
        load(subscriptionIds.toList(), force = true)
    }

    private fun load(subscriptionIds: List<Long>, force: Boolean = false) {
        val subscriptions = state.value.subscriptions
            .filter { it.id in subscriptionIds }
            .filter { force || state.value.results[it.id] == null }

        if (subscriptions.isEmpty()) return

        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    subscriptions.forEach { subscription ->
                        results[subscription.id] = SearchItemResult.Loading
                    }
                },
            )
        }

        screenModelScope.launchIO {
            subscriptions.map { subscription ->
                async {
                    semaphore.withPermit {
                        loadSubscription(subscription)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun loadSubscription(subscription: AuthorSubscription) {
        val source = sourceManager.get(subscription.source) as? CatalogueSource
        if (source == null) {
            updateResult(subscription.id, SearchItemResult.Error(IllegalStateException("Source not found")))
            return
        }

        try {
            val page = withContext(searchDispatcher) {
                source.getSearchManga(1, subscription.query.sanitize(), source.getFilterList())
            }
            val titles = page.mangas
                .map { it.toDomainManga(source.id) }
                .distinctBy { it.url }
                .let { networkToLocalManga(it) }

            updateResult(subscription.id, SearchItemResult.Success(titles))
            updateAuthorSubscriptionRefreshTime.await(subscription.id)
        } catch (e: Exception) {
            updateResult(subscription.id, SearchItemResult.Error(e))
        }
    }

    private fun updateResult(subscriptionId: Long, result: SearchItemResult) {
        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    results[subscriptionId] = result
                },
            )
        }
    }

    @Immutable
    data class State(
        val subscriptions: List<AuthorSubscription> = emptyList(),
        val results: PersistentMap<Long, SearchItemResult> = persistentMapOf(),
    )

    companion object {
        private const val INITIAL_LOAD_COUNT = 5
        private const val MAX_CONCURRENT_REQUESTS = 5
    }
}
