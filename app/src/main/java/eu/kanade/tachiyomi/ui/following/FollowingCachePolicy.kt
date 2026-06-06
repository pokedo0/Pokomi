package eu.kanade.tachiyomi.ui.following

internal data class FollowingCachePolicy(
    private val ttlHours: Int,
) {

    val seedPersistentCache: Boolean = ttlHours > 0

    fun shouldRefresh(lastRefreshAt: Long?, hasSuccess: Boolean, force: Boolean, now: Long): Boolean {
        if (force) return true
        if (ttlHours == 0) return true
        val last = lastRefreshAt ?: return true
        val ttlMs = ttlHours * 3600_000L
        return when {
            (now - last) >= ttlMs -> true
            hasSuccess -> false
            else -> true
        }
    }

    companion object {
        fun fromPreference(value: String): FollowingCachePolicy {
            val ttlHours = value.toIntOrNull()?.coerceAtLeast(0) ?: 24
            return FollowingCachePolicy(ttlHours)
        }
    }
}
