package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.tachiyomi.data.backup.models.BackupFollowing

internal fun mergeFollowing(
    localFollowing: BackupFollowing?,
    remoteFollowing: BackupFollowing?,
): BackupFollowing {
    if (localFollowing == null) return remoteFollowing ?: BackupFollowing()
    if (remoteFollowing == null) return localFollowing

    return if (remoteFollowing.lastModifiedAt > localFollowing.lastModifiedAt) {
        remoteFollowing
    } else {
        localFollowing
    }
}
