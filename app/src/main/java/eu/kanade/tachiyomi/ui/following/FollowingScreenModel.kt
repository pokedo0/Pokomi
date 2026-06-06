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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpdateAuthorSubscriptionRefreshTime
import tachiyomi.domain.authorSubscription.interactor.UpsertAuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import androidx.compose.runtime.State as ComposeState

class FollowingScreenModel(
    private val getAuthorSubscriptions: GetAuthorSubscriptions = Injekt.get(),
    private val getAuthorSubscriptionResultCache: GetAuthorSubscriptionResultCache = Injekt.get(),
    private val updateAuthorSubscriptionRefreshTime: UpdateAuthorSubscriptionRefreshTime = Injekt.get(),
    private val upsertAuthorSubscriptionResultCache: UpsertAuthorSubscriptionResultCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val followingPreferences: FollowingPreferences = Injekt.get(),
) : StateScreenModel<FollowingScreenModel.State>(State()) {

    private val searchDispatcher = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS).asCoroutineDispatcher()
    private val sourceRateLimitRetryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val sourceRateLimitController = FollowingSourceRateLimitController(maxAttempts = SOURCE_RATE_LIMIT_MAX_ATTEMPTS)

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

    private fun decideLoad(
        subscription: AuthorSubscription,
        force: Boolean,
        cachePolicy: FollowingCachePolicy,
    ): LoadDecision {
        val hasSuccess = state.value.results[subscription.id] is FollowingItemResult.Success
        return if (
            cachePolicy.shouldRefresh(
                lastRefreshAt = subscription.lastRefreshAt,
                hasSuccess = hasSuccess,
                force = force,
                now = System.currentTimeMillis(),
            )
        ) {
            LoadDecision.Refresh
        } else {
            LoadDecision.Skip
        }
    }

    private fun load(subscriptionIds: List<Long>, force: Boolean = false) {
        val subscriptionsById = state.value.subscriptions
            .filter { it.id in subscriptionIds }

        if (subscriptionsById.isEmpty()) return

        screenModelScope.launchIO {
            val cachePolicy = FollowingCachePolicy.fromPreference(followingPreferences.cacheTtlHours().get())
            val subscriptionsWithCache = if (cachePolicy.seedPersistentCache) {
                seedCache(subscriptionIds, subscriptionsById)
            } else {
                subscriptionsById
            }
            val subscriptions = subscriptionsWithCache
                .filter { decideLoad(it, force, cachePolicy) == LoadDecision.Refresh }

            if (subscriptions.isEmpty()) return@launchIO

            mutableState.update {
                it.copy(
                    results = it.results.mutate { results ->
                        subscriptions.forEach { subscription ->
                            results[subscription.id] = when (val result = results[subscription.id]) {
                                is FollowingItemResult.Success -> result.copy(refreshing = true)
                                else -> it.sourceRateLimits[subscription.source]
                                    ?.toResult(subscription.source)
                                    ?: FollowingItemResult.Loading
                            }
                        }
                    },
                )
            }

            subscriptions
                .map { subscription ->
                    async { loadOne(subscription) }
                }
                .awaitAll()
        }
    }

    private suspend fun seedCache(
        subscriptionIds: List<Long>,
        subscriptions: List<AuthorSubscription>,
    ): List<AuthorSubscription> {
        val existingResultIds = state.value.results
            .filterValues { it is FollowingItemResult.Success }
            .keys
        val uncachedIds = subscriptionIds - existingResultIds
        if (uncachedIds.isEmpty()) return subscriptions

        val caches = getAuthorSubscriptionResultCache.await(uncachedIds)
            .associateBy { it.subscriptionId }
        if (caches.isEmpty()) return subscriptions

        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    caches.forEach { (subscriptionId, cache) ->
                        results[subscriptionId] = FollowingItemResult.Success(cache.mangas)
                    }
                },
            )
        }

        return subscriptions.map { subscription ->
            caches[subscription.id]
                ?.let { subscription.copy(lastRefreshAt = it.cachedAt) }
                ?: subscription
        }
    }

    private suspend fun loadOne(
        subscription: AuthorSubscription,
        ignoreSourceRateLimit: Boolean = false,
        useRetryLane: Boolean = false,
    ): SourceLoadResult {
        val source = sourceManager.get(subscription.source) as? CatalogueSource
        if (source == null) {
            updateResult(subscription.id, FollowingItemResult.Error(IllegalStateException("Source not found")))
            return SourceLoadResult.Finished
        }

        if (shortCircuitRateLimitedSource(subscription, ignoreSourceRateLimit)) {
            return SourceLoadResult.Finished
        }

        try {
            val titles = if (useRetryLane) {
                searchSource(source, subscription, sourceRateLimitRetryDispatcher)
            } else {
                semaphore.acquire()
                try {
                    if (shortCircuitRateLimitedSource(subscription, ignoreSourceRateLimit)) {
                        return SourceLoadResult.Finished
                    }
                    searchSource(source, subscription, searchDispatcher)
                } finally {
                    semaphore.release()
                }
            }
            val refreshedAt = System.currentTimeMillis()
            updateResult(subscription.id, FollowingItemResult.Success(titles))
            recoverSourceRateLimit(subscription.source, subscription.id)
            upsertAuthorSubscriptionResultCache.await(subscription.id, titles, refreshedAt)
            updateAuthorSubscriptionRefreshTime.await(subscription.id, refreshedAt)
            return SourceLoadResult.Finished
        } catch (e: HttpException) {
            if (e.code != 429) {
                updateRefreshFailure(subscription.id, FollowingItemResult.Error(e))
                return SourceLoadResult.Finished
            }
            val rateLimit = sourceRateLimitController.open(subscription.source, listOf(subscription.id))
            updateRateLimitedSource(
                sourceId = subscription.source,
                subscriptionIds = listOf(subscription.id),
                rateLimit = rateLimit,
            )
            startSourceRetry(subscription.source)
            return SourceLoadResult.RateLimited
        } catch (e: Exception) {
            updateRefreshFailure(subscription.id, FollowingItemResult.Error(e))
            return SourceLoadResult.Finished
        }
    }

    private suspend fun searchSource(
        source: CatalogueSource,
        subscription: AuthorSubscription,
        dispatcher: CoroutineContext,
    ): List<Manga> {
        val page = withContext(dispatcher) {
            source.getSearchManga(1, subscription.query.sanitize(), source.getFilterList())
        }
        return page.mangas
            .map { it.toDomainManga(source.id) }
            .distinctBy { it.url }
            .let { networkToLocalManga(it) }
    }

    private fun shortCircuitRateLimitedSource(
        subscription: AuthorSubscription,
        ignoreSourceRateLimit: Boolean,
    ): Boolean {
        if (ignoreSourceRateLimit) return false

        val rateLimit = sourceRateLimitController.join(subscription.source, listOf(subscription.id)) ?: return false
        updateRateLimitedSource(
            sourceId = subscription.source,
            subscriptionIds = listOf(subscription.id),
            rateLimit = rateLimit,
        )
        startSourceRetry(subscription.source)
        return true
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

    private fun updateRefreshFailure(subscriptionId: Long, result: FollowingItemResult) {
        mutableState.update {
            it.copy(
                results = it.results.mutate { results ->
                    results[subscriptionId] = when (val current = results[subscriptionId]) {
                        is FollowingItemResult.Success -> current.copy(refreshing = result is FollowingItemResult.RateLimited)
                        else -> result
                    }
                },
            )
        }
    }

    private fun updateRateLimitedSource(
        sourceId: Long,
        subscriptionIds: List<Long>,
        rateLimit: FollowingSourceRateLimit,
    ) {
        val activeSubscriptionIds = state.value.activeSourceSubscriptionIds(sourceId, subscriptionIds)
        sourceRateLimitController.addPending(sourceId, activeSubscriptionIds)

        mutableState.update {
            it.copy(
                sourceRateLimits = it.sourceRateLimits.mutate { sourceRateLimits ->
                    sourceRateLimits[sourceId] = rateLimit
                },
                results = it.results.mutate { results ->
                    activeSubscriptionIds.forEach { subscriptionId ->
                        val rateLimited = FollowingItemResult.RateLimited(
                            sourceId = sourceId,
                            attempt = rateLimit.attempt,
                            max = rateLimit.max,
                        )
                        results[subscriptionId] = when (val current = results[subscriptionId]) {
                            is FollowingItemResult.Success -> current.copy(refreshing = true)
                            else -> rateLimited
                        }
                    }
                },
            )
        }
    }

    private fun startSourceRetry(sourceId: Long) {
        if (!sourceRateLimitController.markRetryRunning(sourceId)) return

        screenModelScope.launchIO {
            while (true) {
                val rateLimit = sourceRateLimitController.current(sourceId) ?: return@launchIO
                delay(SOURCE_RATE_LIMIT_BACKOFF_SECONDS[rateLimit.attempt - 1] * 1000L)

                val retrySubscription = state.value.subscriptions
                    .firstOrNull { it.id in sourceRateLimitController.pendingSubscriptionIds(sourceId) }

                if (retrySubscription == null) {
                    sourceRateLimitController.close(sourceId)
                    clearSourceRateLimit(sourceId)
                    return@launchIO
                }

                val retryResult = try {
                    withTimeout(SOURCE_RATE_LIMIT_RETRY_TIMEOUT_MS) {
                        loadOne(retrySubscription, ignoreSourceRateLimit = true, useRetryLane = true)
                    }
                } catch (_: TimeoutCancellationException) {
                    SourceLoadResult.RateLimited
                }
                when (retryResult) {
                    SourceLoadResult.Finished -> {
                        if (sourceRateLimitController.current(sourceId) != null) {
                            recoverSourceRateLimit(sourceId, retrySubscription.id)
                        }
                        return@launchIO
                    }
                    SourceLoadResult.RateLimited -> {
                        val nextRateLimit = sourceRateLimitController.advanceAfterFailedRetry(sourceId)
                        if (nextRateLimit == null) {
                            updateStalledSource(
                                sourceId = sourceId,
                                subscriptionIds = sourceRateLimitController.pendingSubscriptionIds(sourceId),
                            )
                            sourceRateLimitController.close(sourceId)
                            return@launchIO
                        }
                        updateRateLimitedSource(
                            sourceId = sourceId,
                            subscriptionIds = sourceRateLimitController.pendingSubscriptionIds(sourceId),
                            rateLimit = nextRateLimit,
                        )
                    }
                }
            }
        }
    }

    private fun updateStalledSource(sourceId: Long, subscriptionIds: List<Long>) {
        mutableState.update {
            it.copy(
                sourceRateLimits = it.sourceRateLimits.remove(sourceId),
                results = it.results.mutate { results ->
                    val activeSubscriptionIds = it.activeSourceSubscriptionIds(sourceId, subscriptionIds)
                    activeSubscriptionIds.forEach { subscriptionId ->
                        results[subscriptionId] = when (val current = results[subscriptionId]) {
                            is FollowingItemResult.Success -> current.copy(refreshing = false)
                            else -> FollowingItemResult.Stalled
                        }
                    }
                },
            )
        }
    }

    private fun State.activeSourceSubscriptionIds(sourceId: Long, subscriptionIds: List<Long>): Set<Long> {
        val activeIds = subscriptions
            .asSequence()
            .filter { it.source == sourceId }
            .filter { subscription ->
                when (val result = results[subscription.id]) {
                    FollowingItemResult.Loading,
                    is FollowingItemResult.RateLimited,
                    -> true
                    is FollowingItemResult.Success -> result.refreshing
                    else -> false
                }
            }
            .map { it.id }
            .toSet()
        return activeIds + subscriptionIds
    }

    private fun recoverSourceRateLimit(sourceId: Long, completedSubscriptionId: Long) {
        val pendingSubscriptionIds = sourceRateLimitController.recover(sourceId, completedSubscriptionId)
        clearSourceRateLimit(sourceId)
        if (pendingSubscriptionIds.isNotEmpty()) {
            load(pendingSubscriptionIds, force = true)
        }
    }

    private fun clearSourceRateLimit(sourceId: Long) {
        mutableState.update {
            if (sourceId !in it.sourceRateLimits) {
                it
            } else {
                it.copy(sourceRateLimits = it.sourceRateLimits.remove(sourceId))
            }
        }
    }

    @Immutable
    data class State(
        val subscriptions: List<AuthorSubscription> = emptyList(),
        val results: PersistentMap<Long, FollowingItemResult> = persistentMapOf(),
        val sourceRateLimits: PersistentMap<Long, FollowingSourceRateLimit> = persistentMapOf(),
        val activeRankOrderSnapshot: List<AuthorRankOrderSnapshotItem>? = null,
        val pendingRankAnchorId: Long? = null,
        val pendingRankOrderSnapshot: List<AuthorRankOrderSnapshotItem>? = null,
        val highlightedAuthorId: Long? = null,
    )

    companion object {
        private const val INITIAL_LOAD_COUNT = 5
        private const val MAX_CONCURRENT_REQUESTS = 5
        private const val SOURCE_RATE_LIMIT_MAX_ATTEMPTS = 6
        private const val SOURCE_RATE_LIMIT_RETRY_TIMEOUT_MS = 30_000L
        private val SOURCE_RATE_LIMIT_BACKOFF_SECONDS = longArrayOf(10, 20, 40, 80, 160, 300)
    }
}

data class FollowingSourceRateLimit(
    val attempt: Int,
    val max: Int,
) {
    fun toResult(sourceId: Long): FollowingItemResult.RateLimited {
        return FollowingItemResult.RateLimited(
            sourceId = sourceId,
            attempt = attempt,
            max = max,
        )
    }
}

private sealed interface SourceLoadResult {
    data object Finished : SourceLoadResult

    data object RateLimited : SourceLoadResult
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
