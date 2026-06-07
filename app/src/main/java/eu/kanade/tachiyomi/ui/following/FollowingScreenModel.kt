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
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
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
    private var loadedSubscriptionLoadKeys: List<FollowingSubscriptionLoadKey>? = null

    init {
        screenModelScope.launchIO {
            getAuthorSubscriptions.subscribeAll().collectLatest { subscriptions ->
                val loadKeys = subscriptions.toFollowingSubscriptionLoadKeys()
                val shouldLoadInitial = loadedSubscriptionLoadKeys != loadKeys
                trace {
                    "subscriptions collected count=${subscriptions.size} existingResults=${state.value.results.size} " +
                        "loadKeysChanged=$shouldLoadInitial"
                }
                loadedSubscriptionLoadKeys = loadKeys
                mutableState.update { current ->
                    current.copy(
                        subscriptions = subscriptions,
                        results = current.results
                            .filterKeys { id -> subscriptions.any { it.id == id } }
                            .toPersistentMap(),
                    )
                }
                if (shouldLoadInitial) {
                    trace { "subscriptions state updated; loadInitial next state=${state.value.results.followingTraceSummary()}" }
                    loadInitial()
                } else {
                    trace { "subscriptions state updated; loadInitial skipped state=${state.value.results.followingTraceSummary()}" }
                }
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
        val autoLoadAll = followingPreferences.autoLoadAll().get()
        val subscriptionIds = if (autoLoadAll) {
            state.value.subscriptions.map { it.id }
        } else {
            state.value.subscriptions.take(INITIAL_LOAD_COUNT).map { it.id }
        }
        trace {
            "loadInitial autoLoadAll=$autoLoadAll subscriptions=${state.value.subscriptions.size} " +
                "ids=${subscriptionIds.followingTracePreview()}"
        }
        if (autoLoadAll) {
            load(subscriptionIds, reason = "initial-all")
        } else {
            load(subscriptionIds, reason = "initial-visible")
        }
    }

    fun loadVisible(subscriptionId: Long) {
        load(listOf(subscriptionId), reason = "visible")
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
        load(refreshIds.toList(), force = false, reason = "refresh-loaded")
    }

    fun refreshAll() {
        // 全部刷新按钮：常驻，尊重 TTL，只补过期（force=false）
        load(state.value.subscriptions.map { it.id }, force = false, reason = "refresh-all")
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
        load(subscriptionIds.toList(), force = true, reason = "refresh")
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

    private fun load(subscriptionIds: List<Long>, force: Boolean = false, reason: String = "load") {
        trace {
            "load requested reason=$reason force=$force ids=${subscriptionIds.followingTracePreview()}"
        }
        val subscriptionsById = state.value.subscriptions
            .filter { it.id in subscriptionIds }

        if (subscriptionsById.isEmpty()) {
            trace { "load skipped reason=$reason matched=0 requested=${subscriptionIds.size}" }
            return
        }

        screenModelScope.launchIO {
            val cachePolicy = FollowingCachePolicy.fromPreference(followingPreferences.cacheTtlHours().get())
            val subscriptionsWithCache = if (cachePolicy.seedPersistentCache) {
                seedCache(subscriptionIds, subscriptionsById)
            } else {
                subscriptionsById
            }
            val subscriptions = subscriptionsWithCache
                .filter { decideLoad(it, force, cachePolicy) == LoadDecision.Refresh }
            trace {
                "load evaluated reason=$reason requested=${subscriptionIds.size} matched=${subscriptionsById.size} " +
                    "refreshing=${subscriptions.size} skipped=${subscriptionsWithCache.size - subscriptions.size} " +
                    "refreshIds=${subscriptions.map { it.id }.followingTracePreview()}"
            }

            if (subscriptions.isEmpty()) {
                trace { "load skipped reason=$reason no refreshable subscriptions state=${state.value.results.followingTraceSummary()}" }
                return@launchIO
            }

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
            trace {
                "load marked results reason=$reason refreshIds=${subscriptions.map { it.id }.followingTracePreview()} " +
                    "state=${state.value.results.followingTraceSummary()}"
            }

            subscriptions
                .map { subscription ->
                    async { loadOne(subscription) }
                }
                .awaitAll()
            trace {
                "load completed reason=$reason refreshIds=${subscriptions.map { it.id }.followingTracePreview()} " +
                    "state=${state.value.results.followingTraceSummary()}"
            }
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
        if (uncachedIds.isEmpty()) {
            trace { "seedCache skipped all cached ids=${subscriptionIds.followingTracePreview()}" }
            return subscriptions
        }

        val startedAt = System.currentTimeMillis()
        val caches = getAuthorSubscriptionResultCache.await(uncachedIds)
            .associateBy { it.subscriptionId }
        trace {
            "seedCache loaded requested=${uncachedIds.size} found=${caches.size} " +
                "elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
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
        trace {
            "seedCache applied ids=${caches.keys.followingTracePreview()} state=${state.value.results.followingTraceSummary()}"
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
    ): SourceLoadResult {
        trace {
            "loadOne start subscription=${subscription.id} source=${subscription.source} " +
                "ignoreRateLimit=$ignoreSourceRateLimit current=${state.value.results[subscription.id].followingTraceLabel()}"
        }
        val source = sourceManager.get(subscription.source) as? CatalogueSource
        if (source == null) {
            trace { "loadOne missing source subscription=${subscription.id} source=${subscription.source}" }
            updateResult(subscription.id, FollowingItemResult.Error(IllegalStateException("Source not found")))
            return SourceLoadResult.Finished
        }

        if (shortCircuitRateLimitedSource(subscription, ignoreSourceRateLimit)) {
            trace { "loadOne short-circuited before acquire subscription=${subscription.id} source=${subscription.source}" }
            return SourceLoadResult.Finished
        }

        try {
            semaphore.acquire()
            trace { "loadOne acquired semaphore subscription=${subscription.id} source=${subscription.source}" }
            val titles = try {
                if (shortCircuitRateLimitedSource(subscription, ignoreSourceRateLimit)) {
                    trace { "loadOne short-circuited after acquire subscription=${subscription.id} source=${subscription.source}" }
                    return SourceLoadResult.Finished
                }
                searchSource(source, subscription, searchDispatcher)
            } finally {
                semaphore.release()
                trace { "loadOne released semaphore subscription=${subscription.id} source=${subscription.source}" }
            }
            trace { "loadOne finished subscription=${subscription.id} source=${subscription.source} titles=${titles.size}" }
            completeSubscriptionLoad(subscription, titles)
            return SourceLoadResult.Finished
        } catch (e: HttpException) {
            if (e.code != 429) {
                trace {
                    "loadOne non-429 subscription=${subscription.id} source=${subscription.source} code=${e.code} " +
                        "message=${e.message}"
                }
                updateRefreshFailure(subscription.id, FollowingItemResult.Error(e))
                return SourceLoadResult.Finished
            }
            val rateLimit = sourceRateLimitController.open(subscription.source, listOf(subscription.id))
            trace {
                "loadOne source 429 subscription=${subscription.id} source=${subscription.source} " +
                    "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
            }
            updateRateLimitedSource(
                sourceId = subscription.source,
                subscriptionIds = listOf(subscription.id),
                rateLimit = rateLimit,
            )
            startSourceRetry(subscription.source)
            return SourceLoadResult.RateLimited
        } catch (e: Exception) {
            trace {
                "loadOne error subscription=${subscription.id} source=${subscription.source} " +
                    "type=${e::class.simpleName} message=${e.message}"
            }
            updateRefreshFailure(subscription.id, FollowingItemResult.Error(e))
            return SourceLoadResult.Finished
        }
    }

    private suspend fun probeOne(subscription: AuthorSubscription): SourceRetryProbeResult {
        trace { "probeOne start subscription=${subscription.id} source=${subscription.source}" }
        val source = sourceManager.get(subscription.source) as? CatalogueSource
            ?: run {
                trace { "probeOne missing source subscription=${subscription.id} source=${subscription.source}" }
                return SourceRetryProbeResult.Error(IllegalStateException("Source not found"))
            }
        return try {
            val titles = searchSource(source, subscription, sourceRateLimitRetryDispatcher)
            trace { "probeOne success subscription=${subscription.id} source=${subscription.source} titles=${titles.size}" }
            SourceRetryProbeResult.Success(titles)
        } catch (e: HttpException) {
            if (e.code == 429) {
                trace { "probeOne 429 subscription=${subscription.id} source=${subscription.source}" }
                SourceRetryProbeResult.RateLimited
            } else {
                trace {
                    "probeOne non-429 subscription=${subscription.id} source=${subscription.source} code=${e.code} " +
                        "message=${e.message}"
                }
                SourceRetryProbeResult.Error(e)
            }
        } catch (e: Exception) {
            trace {
                "probeOne error subscription=${subscription.id} source=${subscription.source} " +
                    "type=${e::class.simpleName} message=${e.message}"
            }
            SourceRetryProbeResult.Error(e)
        }
    }

    private suspend fun completeSubscriptionLoad(subscription: AuthorSubscription, titles: List<Manga>) {
        val refreshedAt = System.currentTimeMillis()
        trace { "complete start subscription=${subscription.id} source=${subscription.source} titles=${titles.size}" }
        updateResult(subscription.id, FollowingItemResult.Success(titles))
        trace {
            "complete result updated subscription=${subscription.id} state=${state.value.results.followingTraceSummary()}"
        }
        var startedAt = System.currentTimeMillis()
        upsertAuthorSubscriptionResultCache.await(subscription.id, titles, refreshedAt)
        trace {
            "complete cache upserted subscription=${subscription.id} elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
        startedAt = System.currentTimeMillis()
        updateAuthorSubscriptionRefreshTime.await(subscription.id, refreshedAt)
        trace {
            "complete refresh time updated subscription=${subscription.id} elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
    }

    private suspend fun searchSource(
        source: CatalogueSource,
        subscription: AuthorSubscription,
        dispatcher: CoroutineContext,
    ): List<Manga> {
        val startedAt = System.currentTimeMillis()
        val page = withContext(dispatcher) {
            source.getSearchManga(1, subscription.query.sanitize(), source.getFilterList())
        }
        trace {
            "search source returned subscription=${subscription.id} source=${source.id} network=${page.mangas.size} " +
                "elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
        val mapStartedAt = System.currentTimeMillis()
        return page.mangas
            .map { it.toDomainManga(source.id) }
            .distinctBy { it.url }
            .let { networkToLocalManga(it) }
            .also {
                trace {
                    "search mapped local subscription=${subscription.id} source=${source.id} local=${it.size} " +
                        "elapsedMs=${System.currentTimeMillis() - mapStartedAt}"
                }
            }
    }

    private fun shortCircuitRateLimitedSource(
        subscription: AuthorSubscription,
        ignoreSourceRateLimit: Boolean,
    ): Boolean {
        if (ignoreSourceRateLimit) return false

        val rateLimit = sourceRateLimitController.join(subscription.source, listOf(subscription.id)) ?: return false
        trace {
            "shortCircuit joined source=${subscription.source} subscription=${subscription.id} " +
                "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
        }
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
        trace { "updateResult subscription=$subscriptionId result=${result.followingTraceLabel()}" }
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
        trace {
            "updateRefreshFailure subscription=$subscriptionId result=${result.followingTraceLabel()} " +
                "state=${state.value.results.followingTraceSummary()}"
        }
    }

    private fun updateRateLimitedSource(
        sourceId: Long,
        subscriptionIds: List<Long>,
        rateLimit: FollowingSourceRateLimit,
    ) {
        val activeSubscriptionIds = state.value.activeSourceSubscriptionIds(sourceId, subscriptionIds)
        sourceRateLimitController.addPending(sourceId, activeSubscriptionIds)
        trace {
            "updateRateLimitedSource source=$sourceId requested=${subscriptionIds.followingTracePreview()} " +
                "active=${activeSubscriptionIds.followingTracePreview()} " +
                "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
        }

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
        trace {
            "updateRateLimitedSource applied source=$sourceId state=${state.value.results.followingTraceSummary()}"
        }
    }

    private fun startSourceRetry(sourceId: Long) {
        if (!sourceRateLimitController.markRetryRunning(sourceId)) {
            trace { "retry not started source=$sourceId already running or missing" }
            return
        }

        trace { "retry started source=$sourceId" }
        screenModelScope.launchIO {
            while (true) {
                val rateLimit = sourceRateLimitController.current(sourceId) ?: run {
                    trace { "retry stop source=$sourceId no current rate limit" }
                    return@launchIO
                }
                val delaySeconds = SOURCE_RATE_LIMIT_BACKOFF_SECONDS[rateLimit.attempt - 1]
                trace {
                    "retry sleeping source=$sourceId attempt=${rateLimit.attempt}/${rateLimit.max} " +
                        "generation=${rateLimit.generation} delaySeconds=$delaySeconds " +
                        "pending=${sourceRateLimitController.pendingSubscriptionIds(sourceId).followingTracePreview()}"
                }
                delay(delaySeconds * 1000L)

                if (sourceRateLimitController.current(sourceId)?.generation != rateLimit.generation) {
                    trace {
                        "retry stop source=$sourceId stale generation=${rateLimit.generation} " +
                            "current=${sourceRateLimitController.current(sourceId)?.generation}"
                    }
                    return@launchIO
                }

                val retrySubscription = state.value.subscriptions
                    .firstOrNull { it.id in sourceRateLimitController.pendingSubscriptionIds(sourceId) }

                if (retrySubscription == null) {
                    trace { "retry stop source=$sourceId no pending subscription" }
                    sourceRateLimitController.close(sourceId)
                    clearSourceRateLimit(sourceId)
                    return@launchIO
                }

                trace {
                    "retry probing source=$sourceId subscription=${retrySubscription.id} " +
                        "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
                }
                val retryResult = try {
                    withTimeout(SOURCE_RATE_LIMIT_RETRY_TIMEOUT_MS) {
                        probeOne(retrySubscription)
                    }
                } catch (_: TimeoutCancellationException) {
                    trace {
                        "retry probe timeout source=$sourceId subscription=${retrySubscription.id} " +
                            "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
                    }
                    SourceRetryProbeResult.RateLimited
                }
                when (retryResult) {
                    is SourceRetryProbeResult.Success -> {
                        trace {
                            "retry probe success source=$sourceId subscription=${retrySubscription.id} " +
                                "titles=${retryResult.titles.size} generation=${rateLimit.generation}"
                        }
                        completeSubscriptionLoad(retrySubscription, retryResult.titles)
                        if (sourceRateLimitController.current(sourceId)?.generation == rateLimit.generation) {
                            val pendingSubscriptionIds = recoverSourceRateLimit(sourceId, retrySubscription.id, rateLimit.generation)
                            drainRecoveredSourceSerially(sourceId, pendingSubscriptionIds)
                        }
                        return@launchIO
                    }
                    is SourceRetryProbeResult.Error -> {
                        trace {
                            "retry probe error source=$sourceId subscription=${retrySubscription.id} " +
                                "type=${retryResult.throwable::class.simpleName} generation=${rateLimit.generation}"
                        }
                        updateRefreshFailure(retrySubscription.id, FollowingItemResult.Error(retryResult.throwable))
                        if (sourceRateLimitController.current(sourceId)?.generation == rateLimit.generation) {
                            val pendingSubscriptionIds = recoverSourceRateLimit(sourceId, retrySubscription.id, rateLimit.generation)
                            drainRecoveredSourceSerially(sourceId, pendingSubscriptionIds)
                        }
                        return@launchIO
                    }
                    SourceRetryProbeResult.RateLimited -> {
                        val nextRateLimit = sourceRateLimitController.advanceAfterFailedRetry(
                            sourceId = sourceId,
                            generation = rateLimit.generation,
                        )
                        if (nextRateLimit == null) {
                            if (sourceRateLimitController.current(sourceId)?.generation != rateLimit.generation) {
                                trace {
                                    "retry failed source=$sourceId stale before stalled generation=${rateLimit.generation} " +
                                        "current=${sourceRateLimitController.current(sourceId)?.generation}"
                                }
                                return@launchIO
                            }
                            trace {
                                "retry exhausted source=$sourceId generation=${rateLimit.generation} " +
                                    "pending=${sourceRateLimitController.pendingSubscriptionIds(sourceId).followingTracePreview()}"
                            }
                            updateStalledSource(
                                sourceId = sourceId,
                                subscriptionIds = sourceRateLimitController.pendingSubscriptionIds(sourceId),
                            )
                            sourceRateLimitController.close(sourceId)
                            return@launchIO
                        }
                        trace {
                            "retry advanced source=$sourceId attempt=${nextRateLimit.attempt}/${nextRateLimit.max} " +
                                "generation=${nextRateLimit.generation}"
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
        trace {
            "updateStalledSource applied source=$sourceId subscriptionIds=${subscriptionIds.followingTracePreview()} " +
                "state=${state.value.results.followingTraceSummary()}"
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

    private suspend fun drainRecoveredSourceSerially(sourceId: Long, subscriptionIds: List<Long>) {
        if (subscriptionIds.isEmpty()) {
            trace { "serial recovery skipped source=$sourceId no pending subscriptions" }
            return
        }
        trace {
            "serial recovery started source=$sourceId pending=${subscriptionIds.followingTracePreview()}"
        }

        subscriptionIds.forEachIndexed { index, subscriptionId ->
            val subscription = state.value.subscriptions.firstOrNull { it.id == subscriptionId }
            if (subscription == null) {
                trace { "serial recovery skip missing subscription source=$sourceId subscription=$subscriptionId" }
                return@forEachIndexed
            }
            if (subscription.source != sourceId) {
                trace {
                    "serial recovery skip source mismatch source=$sourceId subscription=$subscriptionId " +
                        "actualSource=${subscription.source}"
                }
                return@forEachIndexed
            }

            if (index > 0) {
                delay(SOURCE_RECOVERY_SERIAL_DELAY_MS)
            }

            trace {
                "serial recovery probing source=$sourceId subscription=$subscriptionId " +
                    "index=${index + 1}/${subscriptionIds.size}"
            }
            when (val result = probeOne(subscription)) {
                is SourceRetryProbeResult.Success -> {
                    trace {
                        "serial recovery success source=$sourceId subscription=$subscriptionId " +
                            "titles=${result.titles.size}"
                    }
                    completeSubscriptionLoad(subscription, result.titles)
                }
                is SourceRetryProbeResult.Error -> {
                    trace {
                        "serial recovery error source=$sourceId subscription=$subscriptionId " +
                            "type=${result.throwable::class.simpleName}"
                    }
                    updateRefreshFailure(subscriptionId, FollowingItemResult.Error(result.throwable))
                }
                SourceRetryProbeResult.RateLimited -> {
                    val remainingIds = subscriptionIds.drop(index)
                    val rateLimit = sourceRateLimitController.open(sourceId, remainingIds)
                    trace {
                        "serial recovery 429 source=$sourceId subscription=$subscriptionId " +
                            "remaining=${remainingIds.followingTracePreview()} " +
                            "attempt=${rateLimit.attempt}/${rateLimit.max} generation=${rateLimit.generation}"
                    }
                    updateRateLimitedSource(
                        sourceId = sourceId,
                        subscriptionIds = remainingIds,
                        rateLimit = rateLimit,
                    )
                    startSourceRetry(sourceId)
                    return
                }
            }
        }

        trace {
            "serial recovery completed source=$sourceId state=${state.value.results.followingTraceSummary()}"
        }
    }

    private fun recoverSourceRateLimit(sourceId: Long, completedSubscriptionId: Long, generation: Long): List<Long> {
        val pendingSubscriptionIds = sourceRateLimitController.consumeRecoveredPending(sourceId, completedSubscriptionId, generation)
            ?: run {
                trace {
                    "recover ignored stale source=$sourceId completed=$completedSubscriptionId generation=$generation " +
                        "current=${sourceRateLimitController.current(sourceId)?.generation}"
                }
                return emptyList()
            }
        trace {
            "recover source=$sourceId completed=$completedSubscriptionId generation=$generation " +
                "pending=${pendingSubscriptionIds.followingTracePreview()}"
        }
        clearSourceRateLimit(sourceId)
        return pendingSubscriptionIds
    }

    private fun clearSourceRateLimit(sourceId: Long, stalledSubscriptionIds: List<Long> = emptyList()) {
        mutableState.update {
            if (sourceId !in it.sourceRateLimits) {
                trace { "clearSourceRateLimit skipped source=$sourceId missing" }
                it
            } else {
                it.copy(
                    sourceRateLimits = it.sourceRateLimits.remove(sourceId),
                    results = it.results.mutate { results ->
                        stalledSubscriptionIds.forEach { subscriptionId ->
                            results[subscriptionId] = when (val current = results[subscriptionId]) {
                                is FollowingItemResult.Success -> current.copy(refreshing = false)
                                else -> FollowingItemResult.Stalled
                            }
                        }
                    },
                )
            }
        }
        trace {
            "clearSourceRateLimit applied source=$sourceId stalled=${stalledSubscriptionIds.followingTracePreview()} " +
                "state=${state.value.results.followingTraceSummary()}"
        }
    }

    private fun trace(message: () -> String) {
        logcat(LogPriority.DEBUG, tag = TRACE_TAG) { message() }
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
        private const val TRACE_TAG = "FollowingTrace"
        private const val INITIAL_LOAD_COUNT = 5
        private const val MAX_CONCURRENT_REQUESTS = 5
        private const val SOURCE_RATE_LIMIT_MAX_ATTEMPTS = 6
        private const val SOURCE_RATE_LIMIT_RETRY_TIMEOUT_MS = 30_000L
        private const val SOURCE_RECOVERY_SERIAL_DELAY_MS = 1_000L
        private val SOURCE_RATE_LIMIT_BACKOFF_SECONDS = longArrayOf(10, 20, 30, 45, 60, 90)
    }
}

data class FollowingSourceRateLimit(
    val attempt: Int,
    val max: Int,
    val generation: Long,
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

private sealed interface SourceRetryProbeResult {
    data class Success(val titles: List<Manga>) : SourceRetryProbeResult

    data class Error(val throwable: Throwable) : SourceRetryProbeResult

    data object RateLimited : SourceRetryProbeResult
}

private fun FollowingItemResult?.followingTraceLabel(): String {
    return when (this) {
        null -> "null"
        FollowingItemResult.Loading -> "Loading"
        is FollowingItemResult.RateLimited -> "RateLimited(source=$sourceId,attempt=$attempt/$max)"
        FollowingItemResult.Stalled -> "Stalled"
        is FollowingItemResult.Error -> "Error(${throwable::class.simpleName}:${throwable.message})"
        is FollowingItemResult.Success -> "Success(size=${result.size},refreshing=$refreshing)"
    }
}

private fun Map<Long, FollowingItemResult>.followingTraceSummary(): String {
    var loading = 0
    var rateLimited = 0
    var stalled = 0
    var success = 0
    var refreshing = 0
    var error = 0
    values.forEach { result ->
        when (result) {
            FollowingItemResult.Loading -> loading += 1
            is FollowingItemResult.RateLimited -> rateLimited += 1
            FollowingItemResult.Stalled -> stalled += 1
            is FollowingItemResult.Success -> {
                success += 1
                if (result.refreshing) refreshing += 1
            }
            is FollowingItemResult.Error -> error += 1
        }
    }
    return "loading=$loading rateLimited=$rateLimited stalled=$stalled success=$success refreshing=$refreshing error=$error"
}

private fun Iterable<Long>.followingTracePreview(limit: Int = 12): String {
    val values = take(limit + 1)
    val visible = values.take(limit).joinToString(prefix = "[", postfix = if (values.size > limit) ", ...]" else "]")
    return visible
}

@Immutable
internal data class FollowingSubscriptionLoadKey(
    val id: Long,
    val source: Long,
    val name: String,
    val query: String,
    val normalizedQuery: String,
    val sortOrder: Long,
    val pinned: Boolean,
)

internal fun List<AuthorSubscription>.toFollowingSubscriptionLoadKeys(): List<FollowingSubscriptionLoadKey> {
    return map {
        FollowingSubscriptionLoadKey(
            id = it.id,
            source = it.source,
            name = it.name,
            query = it.query,
            normalizedQuery = it.normalizedQuery,
            sortOrder = it.sortOrder,
            pinned = it.pinned,
        )
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
