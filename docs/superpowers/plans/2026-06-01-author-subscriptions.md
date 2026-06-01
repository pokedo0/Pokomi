# Author Subscriptions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Komikku-only Following tab where users subscribe one Browse search keyword to one source, then view that source's ordinary search results in author-style horizontal sections.

**Architecture:** Add a small SQLDelight-backed `author_subscription` domain/data surface, then connect it to two UI surfaces: global search source rows for heart subscribe/switch/remove actions, and a new bottom-nav Following tab for loading saved keyword searches. Search execution should reuse the existing global-search behavior: `CatalogueSource.getSearchManga(1, query.sanitize(), source.getFilterList())`, `toDomainManga`, URL dedupe, then `NetworkToLocalManga`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Voyager tabs/screens, SQLDelight, Injekt, moko-resources `KMR`, existing Browse/global search components.

---

## File Structure

Create these files:

- `data/src/main/sqldelight/tachiyomi/data/author_subscription.sq`: SQLDelight table and queries for author subscriptions.
- `data/src/main/sqldelight/tachiyomi/migrations/46.sqm`: migration that creates the same table and unique index.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/model/AuthorSubscription.kt`: domain model and keyword normalization helper.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/repository/AuthorSubscriptionRepository.kt`: repository contract.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/GetAuthorSubscriptions.kt`: read flow and lookup use case.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpsertAuthorSubscription.kt`: create or switch source binding.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/DeleteAuthorSubscription.kt`: remove subscription.
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpdateAuthorSubscriptionRefreshTime.kt`: store successful refresh timestamp.
- `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionMapper.kt`: SQLDelight row mapper.
- `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionRepositoryImpl.kt`: repository implementation.
- `domain/src/test/java/tachiyomi/domain/authorSubscription/model/AuthorSubscriptionTest.kt`: pure normalization tests.
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingTab.kt`: bottom navigation tab.
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingScreenModel.kt`: subscription screen state and rate-limited search loading.
- `app/src/main/java/eu/kanade/presentation/following/FollowingScreen.kt`: Following tab UI.

Modify these files:

- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`: register repository and interactors.
- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`: insert `FollowingTab` between Library and Updates.
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt`: update tab index from `3u` to `4u`.
- `app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`: update index after Following is inserted.
- `app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt`: update index after Following is inserted.
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt`: update index after Following is inserted.
- `app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt`: add optional heart action to source result headers.
- `app/src/main/java/eu/kanade/presentation/browse/GlobalSearchScreen.kt`: pass heart state/action into each source row.
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt`: wire heart actions to domain interactors and confirmation dialog.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`: add Komikku strings only.

Generated or touched by commands:

- SQLDelight generated sources after `./gradlew :data:generateSqlDelightInterface`.
- Formatting changes after `./gradlew spotlessApply`.

---

### Task 1: Database, Domain Model, Repository, and Interactors

**Files:**
- Create: `data/src/main/sqldelight/tachiyomi/data/author_subscription.sq`
- Create: `data/src/main/sqldelight/tachiyomi/migrations/46.sqm`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/model/AuthorSubscription.kt`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/repository/AuthorSubscriptionRepository.kt`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/GetAuthorSubscriptions.kt`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpsertAuthorSubscription.kt`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/DeleteAuthorSubscription.kt`
- Create: `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpdateAuthorSubscriptionRefreshTime.kt`
- Create: `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionMapper.kt`
- Create: `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionRepositoryImpl.kt`
- Create: `domain/src/test/java/tachiyomi/domain/authorSubscription/model/AuthorSubscriptionTest.kt`
- Modify: `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`

- [ ] **Step 1: Write the pure normalization test**

Create `domain/src/test/java/tachiyomi/domain/authorSubscription/model/AuthorSubscriptionTest.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorSubscriptionTest {

    @Test
    fun `normalizes surrounding and repeated whitespace`() {
        assertEquals("tatsuki fujimoto", AuthorSubscription.normalizeQuery("  Tatsuki   Fujimoto  "))
    }

    @Test
    fun `normalizes mixed case`() {
        assertEquals("one", AuthorSubscription.normalizeQuery("ONE"))
    }

    @Test
    fun `keeps non-latin keywords while trimming`() {
        assertEquals("藤本タツキ", AuthorSubscription.normalizeQuery("  藤本タツキ  "))
    }
}
```

- [ ] **Step 2: Run the normalization test and verify it fails**

Run:

```bash
./gradlew :domain:test --tests tachiyomi.domain.authorSubscription.model.AuthorSubscriptionTest
```

Expected: compilation fails because `AuthorSubscription` does not exist yet.

- [ ] **Step 3: Add the domain model**

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/model/AuthorSubscription.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.model

data class AuthorSubscription(
    val id: Long,
    val source: Long,
    val name: String,
    val query: String,
    val normalizedQuery: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRefreshAt: Long?,
    val sortOrder: Long,
) {
    companion object {
        fun normalizeQuery(query: String): String {
            return query
                .trim()
                .replace(Regex("\\s+"), " ")
                .lowercase()
        }
    }
}
```

- [ ] **Step 4: Run the normalization test and verify it passes**

Run:

```bash
./gradlew :domain:test --tests tachiyomi.domain.authorSubscription.model.AuthorSubscriptionTest
```

Expected: test passes.

- [ ] **Step 5: Add SQLDelight schema**

Create `data/src/main/sqldelight/tachiyomi/data/author_subscription.sq`:

```sql
CREATE TABLE author_subscription(
    _id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    name TEXT NOT NULL,
    query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL,
    last_refresh_at INTEGER AS Long,
    sort_order INTEGER NOT NULL
);

CREATE UNIQUE INDEX author_subscription_normalized_query_index
ON author_subscription(normalized_query);

selectAll:
SELECT *
FROM author_subscription
ORDER BY sort_order ASC, _id ASC;

selectAllByFlow:
SELECT *
FROM author_subscription
ORDER BY sort_order ASC, _id ASC;

selectById:
SELECT *
FROM author_subscription
WHERE _id = :id;

selectByNormalizedQuery:
SELECT *
FROM author_subscription
WHERE normalized_query = :normalizedQuery;

insert:
INSERT INTO author_subscription(
    source,
    name,
    query,
    normalized_query,
    created_at,
    updated_at,
    last_refresh_at,
    sort_order
)
VALUES (
    :source,
    :name,
    :query,
    :normalizedQuery,
    :createdAt,
    :updatedAt,
    :lastRefreshAt,
    COALESCE((SELECT MAX(sort_order) + 1 FROM author_subscription), 0)
);

updateSource:
UPDATE author_subscription
SET
    source = :source,
    name = :name,
    query = :query,
    updated_at = :updatedAt
WHERE normalized_query = :normalizedQuery;

updateLastRefreshAt:
UPDATE author_subscription
SET
    last_refresh_at = :lastRefreshAt,
    updated_at = :updatedAt
WHERE _id = :id;

deleteById:
DELETE FROM author_subscription
WHERE _id = :id;

deleteByNormalizedQuery:
DELETE FROM author_subscription
WHERE normalized_query = :normalizedQuery;

selectLastInsertedRowId:
SELECT last_insert_rowid();
```

Create `data/src/main/sqldelight/tachiyomi/migrations/46.sqm` with the same table and index:

```sql
CREATE TABLE author_subscription(
    _id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    name TEXT NOT NULL,
    query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL,
    last_refresh_at INTEGER AS Long,
    sort_order INTEGER NOT NULL
);

CREATE UNIQUE INDEX author_subscription_normalized_query_index
ON author_subscription(normalized_query);
```

- [ ] **Step 6: Generate SQLDelight interfaces**

Run:

```bash
./gradlew :data:generateSqlDelightInterface
```

Expected: SQLDelight succeeds and generated `author_subscriptionQueries` becomes available.

- [ ] **Step 7: Add repository contract**

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/repository/AuthorSubscriptionRepository.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.authorSubscription.model.AuthorSubscription

interface AuthorSubscriptionRepository {
    fun subscribeAll(): Flow<List<AuthorSubscription>>

    suspend fun getAll(): List<AuthorSubscription>

    suspend fun getById(id: Long): AuthorSubscription?

    suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription?

    suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long

    suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long)

    suspend fun deleteById(id: Long)

    suspend fun deleteByNormalizedQuery(normalizedQuery: String)
}
```

- [ ] **Step 8: Add mapper and repository implementation**

Create `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionMapper.kt`:

```kotlin
package tachiyomi.data.authorSubscription

import tachiyomi.domain.authorSubscription.model.AuthorSubscription

object AuthorSubscriptionMapper {
    fun map(
        id: Long,
        source: Long,
        name: String,
        query: String,
        normalizedQuery: String,
        createdAt: Long,
        updatedAt: Long,
        lastRefreshAt: Long?,
        sortOrder: Long,
    ): AuthorSubscription {
        return AuthorSubscription(
            id = id,
            source = source,
            name = name,
            query = query,
            normalizedQuery = normalizedQuery,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastRefreshAt = lastRefreshAt,
            sortOrder = sortOrder,
        )
    }
}
```

Create `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionRepositoryImpl.kt`:

```kotlin
package tachiyomi.data.authorSubscription

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class AuthorSubscriptionRepositoryImpl(
    private val handler: DatabaseHandler,
) : AuthorSubscriptionRepository {

    override fun subscribeAll(): Flow<List<AuthorSubscription>> {
        return handler.subscribeToList {
            author_subscriptionQueries.selectAll(AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getAll(): List<AuthorSubscription> {
        return handler.awaitList {
            author_subscriptionQueries.selectAll(AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getById(id: Long): AuthorSubscription? {
        return handler.awaitOneOrNull {
            author_subscriptionQueries.selectById(id, AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? {
        return handler.awaitOneOrNull {
            author_subscriptionQueries.selectByNormalizedQuery(normalizedQuery, AuthorSubscriptionMapper::map)
        }
    }

    override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long {
        val now = System.currentTimeMillis()
        return handler.await(true) {
            val existing = handler.awaitOneOrNull {
                author_subscriptionQueries.selectByNormalizedQuery(normalizedQuery, AuthorSubscriptionMapper::map)
            }
            if (existing == null) {
                handler.awaitOneExecutable(true) {
                    author_subscriptionQueries.insert(
                        source = source,
                        name = name,
                        query = query,
                        normalizedQuery = normalizedQuery,
                        createdAt = now,
                        updatedAt = now,
                        lastRefreshAt = null,
                    )
                    author_subscriptionQueries.selectLastInsertedRowId()
                }
            } else {
                author_subscriptionQueries.updateSource(
                    source = source,
                    name = name,
                    query = query,
                    updatedAt = now,
                    normalizedQuery = normalizedQuery,
                )
                existing.id
            }
        }
    }

    override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) {
        handler.await {
            author_subscriptionQueries.updateLastRefreshAt(
                id = id,
                lastRefreshAt = lastRefreshAt,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await { author_subscriptionQueries.deleteById(id) }
    }

    override suspend fun deleteByNormalizedQuery(normalizedQuery: String) {
        handler.await { author_subscriptionQueries.deleteByNormalizedQuery(normalizedQuery) }
    }
}
```

- [ ] **Step 9: Add interactors**

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/GetAuthorSubscriptions.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class GetAuthorSubscriptions(
    private val repository: AuthorSubscriptionRepository,
) {
    fun subscribeAll(): Flow<List<AuthorSubscription>> {
        return repository.subscribeAll()
    }

    suspend fun awaitAll(): List<AuthorSubscription> {
        return repository.getAll()
    }

    suspend fun awaitByQuery(query: String): AuthorSubscription? {
        return repository.getByNormalizedQuery(AuthorSubscription.normalizeQuery(query))
    }
}
```

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpsertAuthorSubscription.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class UpsertAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
) {
    suspend fun await(source: Long, query: String, name: String = query.trim()): Long {
        val normalizedQuery = AuthorSubscription.normalizeQuery(query)
        require(normalizedQuery.isNotBlank()) { "Author subscription query must not be blank" }
        return repository.upsert(
            source = source,
            name = name.trim().ifBlank { query.trim() },
            query = query.trim(),
            normalizedQuery = normalizedQuery,
        )
    }
}
```

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/DeleteAuthorSubscription.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class DeleteAuthorSubscription(
    private val repository: AuthorSubscriptionRepository,
) {
    suspend fun awaitById(id: Long) {
        repository.deleteById(id)
    }

    suspend fun awaitByQuery(query: String) {
        repository.deleteByNormalizedQuery(AuthorSubscription.normalizeQuery(query))
    }
}
```

Create `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/UpdateAuthorSubscriptionRefreshTime.kt`:

```kotlin
package tachiyomi.domain.authorSubscription.interactor

import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository

class UpdateAuthorSubscriptionRefreshTime(
    private val repository: AuthorSubscriptionRepository,
) {
    suspend fun await(id: Long, lastRefreshAt: Long = System.currentTimeMillis()) {
        repository.updateLastRefreshAt(id, lastRefreshAt)
    }
}
```

- [ ] **Step 10: Register dependencies in KMKDomainModule**

Modify `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` by adding imports:

```kotlin
import tachiyomi.data.authorSubscription.AuthorSubscriptionRepositoryImpl
import tachiyomi.domain.authorSubscription.interactor.DeleteAuthorSubscription
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpdateAuthorSubscriptionRefreshTime
import tachiyomi.domain.authorSubscription.interactor.UpsertAuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
```

Add registrations inside `registerInjectables()`:

```kotlin
// KMK -->
addSingletonFactory<AuthorSubscriptionRepository> { AuthorSubscriptionRepositoryImpl(get()) }
addFactory { GetAuthorSubscriptions(get()) }
addFactory { UpsertAuthorSubscription(get()) }
addFactory { DeleteAuthorSubscription(get()) }
addFactory { UpdateAuthorSubscriptionRefreshTime(get()) }
// KMK <--
```

- [ ] **Step 11: Run domain and SQLDelight checks**

Run:

```bash
./gradlew :domain:test --tests tachiyomi.domain.authorSubscription.model.AuthorSubscriptionTest
./gradlew :data:generateSqlDelightInterface
```

Expected: both commands pass.

- [ ] **Step 12: Commit the data/domain foundation**

Run:

```bash
git add data/src/main/sqldelight/tachiyomi/data/author_subscription.sq \
  data/src/main/sqldelight/tachiyomi/migrations/46.sqm \
  domain/src/main/java/tachiyomi/domain/authorSubscription \
  data/src/main/java/tachiyomi/data/authorSubscription \
  domain/src/test/java/tachiyomi/domain/authorSubscription \
  app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
git commit -m "feat(following): add author subscription data model"
```

---

### Task 2: Following Screen Search State

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingScreenModel.kt`

- [ ] **Step 1: Create the state model**

Create `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingScreenModel.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

class FollowingScreenModel(
    private val getAuthorSubscriptions: GetAuthorSubscriptions = Injekt.get(),
    private val updateAuthorSubscriptionRefreshTime: UpdateAuthorSubscriptionRefreshTime = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<FollowingScreenModel.State>(State()) {

    private val searchDispatcher = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS).asCoroutineDispatcher()
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private var loadJob: Job? = null

    init {
        screenModelScope.launch {
            getAuthorSubscriptions.subscribeAll().collectLatest { subscriptions ->
                mutableState.update { current ->
                    current.copy(
                        subscriptions = subscriptions,
                        results = current.results.filterKeys { id ->
                            subscriptions.any { it.id == id }
                        }.toPersistentMap(),
                    )
                }
                loadInitial()
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { value = it }
        }
    }

    fun loadInitial() {
        val ids = state.value.subscriptions.take(INITIAL_LOAD_COUNT).map { it.id }
        load(ids)
    }

    fun loadVisible(subscriptionId: Long) {
        load(listOf(subscriptionId))
    }

    fun refresh(subscriptionId: Long) {
        mutableState.update {
            it.copy(results = it.results.mutate { results -> results[subscriptionId] = SearchItemResult.Loading })
        }
        load(listOf(subscriptionId), force = true)
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

        loadJob?.cancel()
        loadJob = screenModelScope.launchIO {
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
            it.copy(results = it.results.mutate { results -> results[subscriptionId] = result })
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
```

- [ ] **Step 2: Compile the app module to catch missing imports or APIs**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compilation reaches Kotlin and reports no errors in `FollowingScreenModel.kt`.

- [ ] **Step 3: Commit the screen model**

Run:

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingScreenModel.kt
git commit -m "feat(following): load subscribed author searches"
```

---

### Task 3: Following Tab UI and Bottom Navigation

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingTab.kt`
- Create: `app/src/main/java/eu/kanade/presentation/following/FollowingScreen.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt`
- Modify: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

- [ ] **Step 1: Add Komikku strings**

Modify `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` and add these base strings:

```xml
<string name="following">Following</string>
<string name="following_empty">No followed authors</string>
<string name="following_refresh_author">Refresh author</string>
<string name="following_open_author_search">Open author search</string>
<string name="following_source_missing">Source not found</string>
```

- [ ] **Step 2: Create the presentation UI**

Create `app/src/main/java/eu/kanade/presentation/following/FollowingScreen.kt`:

```kotlin
package eu.kanade.presentation.following

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun FollowingScreen(
    subscriptions: List<AuthorSubscription>,
    results: Map<Long, SearchItemResult>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
    onVisible: (Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.following),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (subscriptions.isEmpty()) {
            tachiyomi.presentation.core.screens.EmptyScreen(
                message = stringResource(KMR.strings.following_empty),
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            LazyColumn(contentPadding = paddingValues) {
                items(subscriptions, key = { it.id }) { subscription ->
                    FollowingAuthorSection(
                        subscription = subscription,
                        result = results[subscription.id],
                        getManga = getManga,
                        onClickManga = onClickManga,
                        onLongClickManga = onLongClickManga,
                        onRefresh = onRefresh,
                        onOpenSearch = onOpenSearch,
                        onVisible = onVisible,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowingAuthorSection(
    subscription: AuthorSubscription,
    result: SearchItemResult?,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
    onVisible: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.LaunchedEffect(subscription.id) {
        onVisible(subscription.id)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.extraSmall,
                    top = MaterialTheme.padding.medium,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subscription.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Row {
                IconButton(onClick = { onRefresh(subscription.id) }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(KMR.strings.following_refresh_author),
                    )
                }
                IconButton(onClick = { onOpenSearch(subscription.query) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(KMR.strings.following_open_author_search),
                    )
                }
            }
        }

        when (result) {
            null,
            SearchItemResult.Loading,
            -> GlobalSearchLoadingResultItem()
            is SearchItemResult.Success -> GlobalSearchCardRow(
                titles = result.result,
                getManga = getManga,
                onClick = onClickManga,
                onLongClick = onLongClickManga,
                selection = emptyList(),
            )
            is SearchItemResult.Error -> GlobalSearchErrorResultItem(result.throwable.message)
        }
    }
}
```

- [ ] **Step 3: Create the Voyager tab**

Create `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingTab.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.following

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.following.FollowingScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

data object FollowingTab : Tab {
    private fun readResolve(): Any = FollowingTab

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 1u,
                title = stringResource(KMR.strings.following),
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Outlined.BookmarkBorder),
            )
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FollowingScreenModel() }
        val state by screenModel.state.collectAsState()

        FollowingScreen(
            subscriptions = state.subscriptions,
            results = state.results,
            getManga = screenModel::getManga,
            onClickManga = { manga -> navigator.push(MangaScreen(manga.id, true)) },
            onLongClickManga = { manga -> navigator.push(MangaScreen(manga.id, true)) },
            onRefresh = screenModel::refresh,
            onOpenSearch = { query -> navigator.push(GlobalSearchScreen(query)) },
            onVisible = screenModel::loadVisible,
        )
    }
}
```

- [ ] **Step 4: Insert Following into HomeScreen**

Modify `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt` imports:

```kotlin
import eu.kanade.tachiyomi.ui.following.FollowingTab
```

Change the tab list:

```kotlin
private val TABS = listOf(
    LibraryTab,
    FollowingTab,
    UpdatesTab,
    HistoryTab,
    BrowseTab,
    MoreTab,
)
```

Do not add a `HomeScreen.Tab.Following` case in the MVP, because no current caller needs to open Following programmatically.

- [ ] **Step 5: Update existing tab indexes**

Set these tab indexes:

- `LibraryTab`: keep `0u`.
- `FollowingTab`: `1u`.
- `UpdatesTab`: `2u`.
- `HistoryTab`: `3u`.
- `BrowseTab`: `4u`.
- `MoreTab`: `5u`.

Modify each tab's `TabOptions(index = ...)` in:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt
app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt
app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt
```

- [ ] **Step 6: Compile the app**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

- [ ] **Step 7: Commit the Following tab**

Run:

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/following \
  app/src/main/java/eu/kanade/presentation/following \
  app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt \
  i18n-kmk/src/commonMain/moko-resources/base/strings.xml
git commit -m "feat(following): add following tab"
```

---

### Task 4: Global Search Heart Subscription Entry

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/browse/GlobalSearchScreen.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt`
- Modify: `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

- [ ] **Step 1: Add Komikku strings**

Add these strings to `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:

```xml
<string name="following_subscribe_author">Subscribe author</string>
<string name="following_unsubscribe_author">Unsubscribe author</string>
<string name="following_switch_source">Switch subscribed source</string>
<string name="following_unsubscribe_confirmation">Remove this author subscription?</string>
<string name="following_unsubscribe_confirm">Remove</string>
```

- [ ] **Step 2: Extend the source result header component**

Modify `GlobalSearchResultItem` in `app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt`.

Add imports:

```kotlin
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.graphics.vector.ImageVector
```

Add parameters:

```kotlin
subscriptionIcon: ImageVector? = null,
subscriptionContentDescription: String? = null,
onClickSubscription: (() -> Unit)? = null,
```

Replace the trailing single `IconButton` with:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    if (subscriptionIcon != null && onClickSubscription != null) {
        IconButton(onClick = onClickSubscription) {
            Icon(
                imageVector = subscriptionIcon,
                contentDescription = subscriptionContentDescription,
            )
        }
    }
    IconButton(onClick = onClick) {
        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
    }
}
```

- [ ] **Step 3: Add subscription parameters to GlobalSearchScreen**

Modify `app/src/main/java/eu/kanade/presentation/browse/GlobalSearchScreen.kt`.

Add imports:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
```

Add parameters to `GlobalSearchScreen`:

```kotlin
subscribedSourceId: Long?,
onClickSubscribeSource: (CatalogueSource) -> Unit,
```

Pass them into `GlobalSearchContent`.

Add parameters to `GlobalSearchContent`:

```kotlin
subscribedSourceId: Long?,
onClickSubscribeSource: (CatalogueSource) -> Unit,
```

When calling `GlobalSearchResultItem`, compute:

```kotlin
val isSubscribedSource = subscribedSourceId == source.id
```

Pass:

```kotlin
subscriptionIcon = if (isSubscribedSource) {
    Icons.Outlined.Favorite
} else {
    Icons.Outlined.FavoriteBorder
},
subscriptionContentDescription = stringResource(
    if (isSubscribedSource) {
        KMR.strings.following_unsubscribe_author
    } else if (subscribedSourceId == null) {
        KMR.strings.following_subscribe_author
    } else {
        KMR.strings.following_switch_source
    },
),
onClickSubscription = { onClickSubscribeSource(source) },
```

Only pass the subscription action when `state.searchQuery` is not blank. Keep the heart hidden for blank searches.

- [ ] **Step 4: Wire global search screen to author subscription interactors**

Modify `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt`.

Add imports:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.authorSubscription.interactor.DeleteAuthorSubscription
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpsertAuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
```

Inside `Content`, create dependencies and state:

```kotlin
val getAuthorSubscriptions = remember { Injekt.get<GetAuthorSubscriptions>() }
val upsertAuthorSubscription = remember { Injekt.get<UpsertAuthorSubscription>() }
val deleteAuthorSubscription = remember { Injekt.get<DeleteAuthorSubscription>() }
val scope = rememberCoroutineScope()
val subscriptions by getAuthorSubscriptions.subscribeAll().collectAsState(initial = emptyList())
var pendingUnsubscribeQuery by remember { mutableStateOf<String?>(null) }
val normalizedSearchQuery = state.searchQuery?.let { AuthorSubscription.normalizeQuery(it) }
val activeSubscription = subscriptions.firstOrNull { it.normalizedQuery == normalizedSearchQuery }
```

Pass into presentation `GlobalSearchScreen`:

```kotlin
subscribedSourceId = activeSubscription?.source,
onClickSubscribeSource = { source ->
    val query = state.searchQuery?.trim().orEmpty()
    if (query.isNotBlank()) {
        if (activeSubscription?.source == source.id) {
            pendingUnsubscribeQuery = query
        } else {
            scope.launchIO {
                upsertAuthorSubscription.await(source = source.id, query = query)
            }
        }
    }
},
```

Add confirmation dialog after `BulkFavoriteDialogs`:

```kotlin
if (pendingUnsubscribeQuery != null) {
    AlertDialog(
        onDismissRequest = { pendingUnsubscribeQuery = null },
        title = { Text(text = stringResource(KMR.strings.following_unsubscribe_author)) },
        text = { Text(text = stringResource(KMR.strings.following_unsubscribe_confirmation)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val query = pendingUnsubscribeQuery ?: return@TextButton
                    pendingUnsubscribeQuery = null
                    scope.launchIO {
                        deleteAuthorSubscription.awaitByQuery(query)
                    }
                },
            ) {
                Text(text = stringResource(KMR.strings.following_unsubscribe_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingUnsubscribeQuery = null }) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
```

- [ ] **Step 5: Compile the app**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

- [ ] **Step 6: Commit the global search entry**

Run:

```bash
git add app/src/main/java/eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt \
  app/src/main/java/eu/kanade/presentation/browse/GlobalSearchScreen.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt \
  i18n-kmk/src/commonMain/moko-resources/base/strings.xml
git commit -m "feat(following): add author subscription heart action"
```

---

### Task 5: Polish, Verification, and Guardrails

**Files:**
- Modify files changed by Tasks 1-4 only if compile, lint, or UX checks reveal issues.

- [ ] **Step 1: Run SQLDelight generation**

Run:

```bash
./gradlew :data:generateSqlDelightInterface
```

Expected: command passes.

- [ ] **Step 2: Run Spotless formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected: command completes and may edit Kotlin/XML formatting.

- [ ] **Step 3: Run Spotless gate**

Run:

```bash
./gradlew spotlessCheck
```

Expected: `BUILD SUCCESSFUL`. If it fails, run `./gradlew spotlessApply` again and re-run `./gradlew spotlessCheck`.

- [ ] **Step 4: Run debug build**

Run:

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Check i18n locale guardrail**

Run:

```bash
git diff --name-only | rg "i18n(-kmk|-sy)?/src/(?!commonMain/moko-resources/base)"
```

Expected: no output. If output appears, revert those non-base locale edits and keep only base strings.

- [ ] **Step 6: Check branch before any push**

Run:

```bash
git branch --show-current
```

Expected: a feature branch such as `design/author-subscriptions`, not `master` or `main`.

- [ ] **Step 7: Final commit**

Run:

```bash
git status --short
git add data domain app i18n-kmk
git commit -m "feat(following): support author subscriptions"
```

Expected: commit succeeds if there are remaining implementation changes not already committed by earlier tasks.

---

## Self-Review

Spec coverage:

- New bottom tab: Task 3.
- Global search source-row heart entry: Task 4.
- One keyword bound to one source: Task 1 unique `normalized_query` plus upsert source switch.
- Following sections hide source name: Task 3 UI uses `subscription.name` only.
- Ordinary Browse keyword search: Task 2 uses `getSearchManga(1, query.sanitize(), source.getFilterList())`.
- Initial load and per-section refresh: Task 2 and Task 3.
- Komikku i18n rules: Task 3 and Task 4 add only `i18n-kmk` base strings.
- Required verification: Task 5.

Placeholder scan:

- The plan contains no placeholder sections.
- Each task has concrete file paths, commands, and expected outcomes.

Type consistency:

- Domain model field `normalizedQuery` maps to SQL `normalized_query`.
- Repository method names match interactor calls.
- UI passes subscription results keyed by `AuthorSubscription.id`.
