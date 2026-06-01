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
