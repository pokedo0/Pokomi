package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpdateAuthorSubscriptionRefreshTime
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
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
    private val followingPreferences: FollowingPreferences = Injekt.get(),
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
        if (followingPreferences.autoLoadAll().get()) {
            load(state.value.subscriptions.map { it.id })
        } else {
            load(state.value.subscriptions.take(INITIAL_LOAD_COUNT).map { it.id })
        }
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
        // 下拉刷新：尊重 TTL，只补过期（force=false）
        load(refreshIds.toList(), force = false)
    }

    fun refreshAll() {
        // 全部刷新按钮：常驻，尊重 TTL，只补过期（force=false）
        load(state.value.subscriptions.map { it.id }, force = false)
    }

    fun onAuthorRankOpened() {
        mutableState.update {
            it.copy(activeRankOrderSnapshot = it.subscriptions.toOrderSnapshot())
        }
    }

    fun onAuthorRankSaved(anchorId: Long?) {
        if (anchorId == null) return

        mutableState.update {
            it.copy(
                pendingRankAnchorId = anchorId,
                pendingRankOrderSnapshot = it.activeRankOrderSnapshot ?: it.subscriptions.toOrderSnapshot(),
                activeRankOrderSnapshot = null,
            )
        }
    }

    fun onAuthorRankAnchorShown(anchorId: Long) {
        mutableState.update {
            if (it.pendingRankAnchorId != anchorId) {
                it
            } else {
                it.copy(
                    pendingRankAnchorId = null,
                    pendingRankOrderSnapshot = null,
                    highlightedAuthorId = anchorId,
                )
            }
        }
    }

    fun clearAuthorRankHighlight(anchorId: Long) {
        mutableState.update {
            if (it.highlightedAuthorId == anchorId) {
                it.copy(highlightedAuthorId = null)
            } else {
                it
            }
        }
    }

    private fun refresh(subscriptionIds: Collection<Long>) {
        // 单作者强制刷新：force=true，无视 TTL
        load(subscriptionIds.toList(), force = true)
    }

    private enum class LoadDecision { Skip, Refresh }

    private fun decideLoad(subscription: AuthorSubscription, force: Boolean): LoadDecision {
        if (force) return LoadDecision.Refresh
        val ttlHours = followingPreferences.cacheTtlHours().get().toIntOrNull()?.coerceAtLeast(0) ?: 24
        val ttlMs = ttlHours * 3600_000L
        val last = subscription.lastRefreshAt
        val hasSuccess = state.value.results[subscription.id] is FollowingItemResult.Success
        return when {
            ttlMs == 0L -> LoadDecision.Refresh
            last == null -> LoadDecision.Refresh
            (System.currentTimeMillis() - last) >= ttlMs -> LoadDecision.Refresh
            hasSuccess -> LoadDecision.Skip
            else -> LoadDecision.Refresh
        }
    }

    private fun load(subscriptionIds: List<Long>, force: Boolean = false) {
        val subscriptions = state.value.subscriptions
            .filter { it.id in subscriptionIds }
            .filter { decideLoad(it, force) == LoadDecision.Refresh }

        if (subscriptions.isEmpty()) return

        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    subscriptions.forEach { subscription ->
                        results[subscription.id] = FollowingItemResult.Loading
                    }
                },
            )
        }

        screenModelScope.launchIO {
            subscriptions.map { subscription ->
                async { loadWithRetry(subscription) }
            }.awaitAll()
        }
    }

    private suspend fun loadWithRetry(subscription: AuthorSubscription) {
        val source = sourceManager.get(subscription.source) as? CatalogueSource
        if (source == null) {
            updateResult(subscription.id, FollowingItemResult.Error(IllegalStateException("Source not found")))
            return
        }

        val backoff = longArrayOf(10, 20, 40, 80, 160, 300) // 秒
        // attempt 0 = 首发；1..6 = 重试
        for (attempt in 0..backoff.size) {
            try {
                semaphore.acquire()
                val titles = try {
                    val page = withContext(searchDispatcher) {
                        source.getSearchManga(1, subscription.query.sanitize(), source.getFilterList())
                    }
                    page.mangas
                        .map { it.toDomainManga(source.id) }
                        .distinctBy { it.url }
                        .let { networkToLocalManga(it) }
                } finally {
                    semaphore.release()
                }
                updateResult(subscription.id, FollowingItemResult.Success(titles))
                updateAuthorSubscriptionRefreshTime.await(subscription.id)
                return
            } catch (e: HttpException) {
                if (e.code != 429) {
                    updateResult(subscription.id, FollowingItemResult.Error(e))
                    return
                }
                if (attempt >= backoff.size) {
                    updateResult(subscription.id, FollowingItemResult.Stalled)
                    return
                }
                // 退避期间不占 semaphore 槽
                updateResult(subscription.id, FollowingItemResult.RateLimited(attempt + 1, backoff.size))
                delay(backoff[attempt] * 1000L)
            } catch (e: Exception) {
                updateResult(subscription.id, FollowingItemResult.Error(e))
                return
            }
        }
        updateResult(subscription.id, FollowingItemResult.Stalled)
    }

    private fun updateResult(subscriptionId: Long, result: FollowingItemResult) {
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
        val results: PersistentMap<Long, FollowingItemResult> = persistentMapOf(),
        val activeRankOrderSnapshot: List<AuthorRankOrderSnapshotItem>? = null,
        val pendingRankAnchorId: Long? = null,
        val pendingRankOrderSnapshot: List<AuthorRankOrderSnapshotItem>? = null,
        val highlightedAuthorId: Long? = null,
    )

    companion object {
        private const val INITIAL_LOAD_COUNT = 5
        private const val MAX_CONCURRENT_REQUESTS = 5
    }
}

@Immutable
data class AuthorRankOrderSnapshotItem(
    val id: Long,
    val sortOrder: Long,
    val pinned: Boolean,
)

private fun List<AuthorSubscription>.toOrderSnapshot(): List<AuthorRankOrderSnapshotItem> {
    return map {
        AuthorRankOrderSnapshotItem(
            id = it.id,
            sortOrder = it.sortOrder,
            pinned = it.pinned,
        )
    }
}
