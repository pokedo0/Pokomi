package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.creators.AuthorSubscriptionBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupAuthorSubscription
import eu.kanade.tachiyomi.data.backup.models.BackupFollowing
import eu.kanade.tachiyomi.data.backup.restore.restorers.AuthorSubscriptionRestorer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionOrderUpdate
import tachiyomi.domain.authorSubscription.model.AuthorSubscriptionResultCache
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.authorSubscription.service.FollowingPreferences

class AuthorSubscriptionBackupRestoreTest {

    @Test
    fun `backup includes following authors with sort order and pinned state`() = runTest {
        val repository = FakeAuthorSubscriptionRepository(
            authors = listOf(
                author(id = 1, name = "Unpinned", sortOrder = 2, pinned = false),
                author(id = 2, name = "Pinned", sortOrder = 0, pinned = true),
            ),
        )
        val preferenceStore = MutablePreferenceStore()
        val preferences = FollowingPreferences(preferenceStore)
        preferences.lastModifiedAt().set(123L)

        val backup = AuthorSubscriptionBackupCreator(
            getAuthorSubscriptions = GetAuthorSubscriptions(repository),
            followingPreferences = preferences,
        )()

        backup.lastModifiedAt shouldBe 123L
        backup.subscriptions.map { it.name } shouldBe listOf("Unpinned", "Pinned")
        backup.subscriptions.map { it.sortOrder } shouldBe listOf(2L, 0L)
        backup.subscriptions.map { it.pinned } shouldBe listOf(false, true)
    }

    @Test
    fun `restore replaces local following when backup is newer and normalizes visual order`() = runTest {
        val repository = FakeAuthorSubscriptionRepository(
            authors = listOf(author(id = 9, name = "Local", sortOrder = 0, pinned = false)),
        )
        val preferenceStore = MutablePreferenceStore()
        val preferences = FollowingPreferences(preferenceStore)
        preferences.lastModifiedAt().set(10L)

        AuthorSubscriptionRestorer(
            repository = repository,
            followingPreferences = preferences,
        ).restoreFollowing(
            BackupFollowing(
                lastModifiedAt = 20L,
                subscriptions = listOf(
                    backupAuthor(name = "Unpinned", sortOrder = 0, pinned = false),
                    backupAuthor(name = "Pinned", sortOrder = 9, pinned = true),
                ),
            ),
        )

        repository.replacedAuthors.map { it.name } shouldBe listOf("Pinned", "Unpinned")
        repository.replacedAuthors.map { it.sortOrder } shouldBe listOf(0L, 1L)
        repository.replacedAuthors.map { it.pinned } shouldBe listOf(true, false)
        preferences.lastModifiedAt().get() shouldBe 20L
    }

    private fun author(
        id: Long,
        name: String,
        sortOrder: Long,
        pinned: Boolean,
    ): AuthorSubscription {
        return AuthorSubscription(
            id = id,
            source = 1L,
            name = name,
            query = name,
            normalizedQuery = AuthorSubscription.normalizeQuery(name),
            createdAt = 1L,
            updatedAt = 2L,
            lastRefreshAt = 3L,
            sortOrder = sortOrder,
            pinned = pinned,
        )
    }

    private fun backupAuthor(
        name: String,
        sortOrder: Long,
        pinned: Boolean,
    ): BackupAuthorSubscription {
        return BackupAuthorSubscription(
            source = 1L,
            name = name,
            query = name,
            normalizedQuery = AuthorSubscription.normalizeQuery(name),
            createdAt = 1L,
            updatedAt = 2L,
            lastRefreshAt = 3L,
            sortOrder = sortOrder,
            pinned = pinned,
        )
    }

    private class FakeAuthorSubscriptionRepository(
        private val authors: List<AuthorSubscription>,
    ) : AuthorSubscriptionRepository {

        var replacedAuthors = emptyList<AuthorSubscription>()

        override fun subscribeAll(): Flow<List<AuthorSubscription>> = emptyFlow()

        override suspend fun getAll(): List<AuthorSubscription> = authors

        override suspend fun getById(id: Long): AuthorSubscription? = authors.find { it.id == id }

        override suspend fun getByNormalizedQuery(normalizedQuery: String): AuthorSubscription? {
            return authors.find { it.normalizedQuery == normalizedQuery }
        }

        override suspend fun upsert(source: Long, name: String, query: String, normalizedQuery: String): Long = 0L

        override suspend fun updateLastRefreshAt(id: Long, lastRefreshAt: Long) = Unit

        override suspend fun replaceAll(subscriptions: List<AuthorSubscription>) {
            replacedAuthors = subscriptions
        }

        override suspend fun getResultCaches(
            subscriptionIds: Collection<Long>,
        ): List<AuthorSubscriptionResultCache> = emptyList()

        override suspend fun upsertResultCache(subscriptionId: Long, mangaIds: List<Long>, cachedAt: Long) = Unit

        override suspend fun updateOrder(updates: List<AuthorSubscriptionOrderUpdate>) = Unit

        override suspend fun updatePinned(id: Long, pinned: Boolean) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteByNormalizedQuery(normalizedQuery: String) = Unit
    }

    private class MutablePreferenceStore : PreferenceStore {

        private val longs = mutableMapOf<String, MutablePreference<Long>>()

        override fun getLong(key: String, defaultValue: Long): Preference<Long> {
            return longs.getOrPut(key) { MutablePreference(key, defaultValue) }
        }

        override fun getString(key: String, defaultValue: String): Preference<String> {
            throw UnsupportedOperationException()
        }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> {
            throw UnsupportedOperationException()
        }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
            throw UnsupportedOperationException()
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
            throw UnsupportedOperationException()
        }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
            throw UnsupportedOperationException()
        }

        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            throw UnsupportedOperationException()
        }

        override fun <T> getObjectFromInt(
            key: String,
            defaultValue: T,
            serializer: (T) -> Int,
            deserializer: (Int) -> T,
        ): Preference<T> {
            throw UnsupportedOperationException()
        }

        override fun getAll(): Map<String, *> = longs
    }

    private class MutablePreference<T>(
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {

        private var data: T? = null
        private val state = MutableStateFlow(defaultValue)

        override fun key(): String = key

        override fun get(): T = data ?: defaultValue

        override fun set(value: T) {
            data = value
            state.value = value
        }

        override fun isSet(): Boolean = data != null

        override fun delete() {
            data = null
            state.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = state

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }
}
