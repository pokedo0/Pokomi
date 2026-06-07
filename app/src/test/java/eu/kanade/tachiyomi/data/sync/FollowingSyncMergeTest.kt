package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAuthorSubscription
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupFollowing
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncService
import eu.kanade.tachiyomi.data.sync.service.mergeFollowing
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class FollowingSyncMergeTest {

    @Test
    fun `remote following wins when remote timestamp is newer`() {
        val local = BackupFollowing(
            lastModifiedAt = 10,
            subscriptions = listOf(author("local")),
        )
        val remote = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("remote")),
        )

        assertEquals(remote, mergeFollowing(local, remote))
    }

    @Test
    fun `local following wins when local timestamp is newer`() {
        val local = BackupFollowing(
            lastModifiedAt = 30,
            subscriptions = listOf(author("local")),
        )
        val remote = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("remote")),
        )

        assertEquals(local, mergeFollowing(local, remote))
    }

    @Test
    fun `remote following wins on first sync when local is unset`() {
        val remote = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("remote")),
        )

        assertEquals(remote, mergeFollowing(null, remote))
    }

    @Test
    fun `local following is kept when timestamps match`() {
        val local = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("local")),
        )
        val remote = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("remote")),
        )

        assertEquals(local, mergeFollowing(local, remote))
    }

    @Test
    fun `sync merge preserves newer following list and feed entries`() {
        val localFollowing = BackupFollowing(
            lastModifiedAt = 10,
            subscriptions = listOf(author("local")),
        )
        val remoteFollowing = BackupFollowing(
            lastModifiedAt = 20,
            subscriptions = listOf(author("remote")),
        )
        val localFeed = BackupFeed(source = 1, global = true)
        val remoteFeed = BackupFeed(
            source = 2,
            global = false,
            savedSearch = BackupSavedSearch(name = "remote", source = 2),
        )

        val mergedBackup = TestSyncService().mergeForTest(
            localSyncData = SyncData(
                backup = Backup(
                    backupManga = emptyList(),
                    backupFeeds = listOf(localFeed),
                    backupFollowing = localFollowing,
                ),
            ),
            remoteSyncData = SyncData(
                backup = Backup(
                    backupManga = emptyList(),
                    backupFeeds = listOf(remoteFeed),
                    backupFollowing = remoteFollowing,
                ),
            ),
        ).backup!!

        assertEquals(remoteFollowing, mergedBackup.backupFollowing)
        assertEquals(listOf(localFeed, remoteFeed), mergedBackup.backupFeeds)
    }

    private fun author(name: String): BackupAuthorSubscription {
        return BackupAuthorSubscription(
            source = 1,
            name = name,
            query = name,
            normalizedQuery = name,
            createdAt = 1,
            updatedAt = 1,
            sortOrder = 0,
            pinned = false,
        )
    }

    private class TestSyncService : SyncService(
        context = mockk<Context>(),
        json = Json,
        syncPreferences = SyncPreferences(InMemoryPreferenceStore()),
    ) {
        override suspend fun doSync(syncData: SyncData) = syncData.backup

        fun mergeForTest(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
            return mergeSyncData(localSyncData, remoteSyncData)
        }
    }
}
