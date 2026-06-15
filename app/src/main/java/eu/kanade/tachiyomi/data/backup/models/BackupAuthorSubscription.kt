package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupFollowing(
    @ProtoNumber(1) val lastModifiedAt: Long = 0,
    @ProtoNumber(2) val subscriptions: List<BackupAuthorSubscription> = emptyList(),
)

@Serializable
data class BackupAuthorSubscription(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val query: String = "",
    @ProtoNumber(4) val normalizedQuery: String = "",
    @ProtoNumber(5) val createdAt: Long = 0,
    @ProtoNumber(6) val updatedAt: Long = 0,
    @ProtoNumber(7) val lastRefreshAt: Long? = null,
    @ProtoNumber(8) val sortOrder: Long = 0,
    @ProtoNumber(9) val pinned: Boolean = false,
)
