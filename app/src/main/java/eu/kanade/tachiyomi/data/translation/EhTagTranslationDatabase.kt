package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class TagSuggestion(
    val keyword: String,
    val hint: String?,
)

@Serializable
data class EhTagTranslationDatabase(
    val entries: List<Entry>,
) {
    val authorTranslations: Map<String, String> = entries
        .asSequence()
        .filter { normalize(it.namespace) in AUTHOR_NAMESPACES }
        .filter { it.translation.isNotEmpty() }
        .associate { normalize(it.keyword) to it.translation }

    val allTranslations: Map<String, String> = entries
        .asSequence()
        .filter { it.translation.isNotEmpty() }
        .fold(linkedMapOf<String, String>()) { translations, entry ->
            translations.putIfAbsent(normalize(entry.keyword), entry.translation)
            translations
        }

    fun translateAuthor(name: String): String? = authorTranslations[normalize(name)]

    fun translateAny(name: String): String? {
        return keywordCandidates(name)
            .mapNotNull { candidate -> allTranslations[candidate] }
            .firstOrNull()
    }

    fun translateTag(namespace: String, keyword: String): String? {
        if (namespace.isBlank() || keyword.isBlank()) return null

        val normalizedNamespace = normalize(namespace)
        val keywordCandidates = keywordCandidates(keyword)
        return keywordCandidates
            .mapNotNull { candidate ->
                tagTranslations[NamespacedTag(normalizedNamespace, candidate)]
            }
            .firstOrNull()
            ?: keywordCandidates
                .mapNotNull { candidate -> allTranslations[candidate] }
                .firstOrNull()
                .takeIf { normalizedNamespace !in namespaces }
    }

    fun containsTag(namespace: String, keyword: String): Boolean {
        return NamespacedTag(normalize(namespace), normalize(keyword)) in normalizedTags
    }

    fun translateAuthorOrNamespacedTag(name: String): String? {
        val separatorIndex = name.indexOf(':')
        if (separatorIndex <= 0) return translateAuthor(name)

        val namespace = name.substring(0, separatorIndex)
        val keyword = name.substring(separatorIndex + 1)
        if (keyword.isBlank()) return null

        return translateTag(namespace, keyword)
            ?.let { "$namespace:$it" }
    }

    fun suggest(query: String, includeTranslations: Boolean, limit: Int): List<TagSuggestion> {
        val keyword = normalize(query)
        if (keyword.isEmpty()) return emptyList()

        return entries
            .asSequence()
            .mapNotNull { entry ->
                val score = entry.score(keyword, includeTranslations) ?: return@mapNotNull null
                ScoredSuggestion(
                    suggestion = TagSuggestion(
                        keyword = entry.keyword,
                        hint = entry.translation.takeIf { includeTranslations && it.isNotEmpty() },
                    ),
                    score = score,
                    namespaceScore = NAMESPACE_SCORES[entry.namespace] ?: 1f,
                )
            }
            .groupBy { normalize(it.suggestion.keyword) }
            .values
            .map { suggestions -> suggestions.minWith(compareBy<ScoredSuggestion> { it.adjustedScore }.thenBy { it.suggestion.keyword }) }
            .sortedWith(compareBy<ScoredSuggestion> { it.adjustedScore }.thenBy { it.suggestion.keyword })
            .take(limit)
            .map { it.suggestion }
    }

    private fun Entry.score(query: String, includeTranslations: Boolean): Float? {
        val tagScore = when {
            keyword.startsWith(query, ignoreCase = true) -> keyword.length.toFloat()
            keyword.indexOf(" $query", ignoreCase = true) != -1 -> keyword.length * 2f
            else -> null
        }
        if (tagScore != null) return tagScore

        if (!includeTranslations) return null

        return when (val index = translation.indexOf(query, ignoreCase = true)) {
            -1 -> null
            0 -> translation.length.toFloat()
            else -> translation.length * 2f
        }
    }

    @Serializable
    data class Entry(
        val namespace: String,
        val keyword: String,
        val translation: String,
    )

    private data class ScoredSuggestion(
        val suggestion: TagSuggestion,
        val score: Float,
        val namespaceScore: Float,
    ) {
        val adjustedScore: Float = score / namespaceScore
    }

    private data class NamespacedTag(
        val namespace: String,
        val keyword: String,
    )

    private fun keywordCandidates(keyword: String): Sequence<String> {
        val normalizedKeyword = normalize(keyword)
        val normalizedKeywordWithoutSuffix = normalize(
            keyword.trimEnd().trimEnd { it in TRAILING_GENDER_SYMBOLS }.trimEnd(),
        )
        return sequenceOf(normalizedKeyword, normalizedKeywordWithoutSuffix).distinct()
    }

    @Transient
    private val tagTranslations: Map<NamespacedTag, String> = entries
        .asSequence()
        .filter { it.translation.isNotEmpty() }
        .fold(linkedMapOf<NamespacedTag, String>()) { translations, entry ->
            translations.putIfAbsent(
                NamespacedTag(normalize(entry.namespace), normalize(entry.keyword)),
                entry.translation,
            )
            translations
        }

    @Transient
    private val namespaces: Set<String> = entries
        .asSequence()
        .map { normalize(it.namespace) }
        .toSet()

    @Transient
    private val normalizedTags: Set<NamespacedTag> = entries
        .asSequence()
        .flatMap {
            sequenceOf(it.keyword, it.translation)
                .filter(String::isNotBlank)
                .map { keyword -> NamespacedTag(normalize(it.namespace), normalize(keyword)) }
        }
        .toSet()

    @Serializable
    private data class TagDatabase(val data: List<NamespaceData> = emptyList())

    @Serializable
    private data class NamespaceData(
        val namespace: String = "",
        val data: Map<String, TagEntry> = emptyMap(),
    )

    @Serializable
    private data class TagEntry(val name: String = "")

    companion object {
        val Empty = EhTagTranslationDatabase(emptyList())

        private val AUTHOR_NAMESPACES = setOf("artist", "group")
        private val TRAILING_GENDER_SYMBOLS = setOf('\u2642', '\u2640')
        private val NAMESPACE_SCORES = mapOf(
            "location" to 10f,
            "other" to 10f,
            "female" to 9f,
            "male" to 8.5f,
            "mixed" to 8f,
            "parody" to 3.3f,
            "character" to 2.8f,
            "artist" to 2.5f,
            "cosplayer" to 2.4f,
            "group" to 2.2f,
            "language" to 2f,
        )

        fun parse(json: Json, raw: String): EhTagTranslationDatabase {
            val database = json.decodeFromString<TagDatabase>(raw)
            val entries = database.data.flatMap { namespace ->
                namespace.data.mapNotNull { (keyword, entry) ->
                    val normalizedKeyword = keyword.trim()
                    if (normalizedKeyword.isEmpty()) {
                        null
                    } else {
                        Entry(
                            namespace = namespace.namespace,
                            keyword = normalizedKeyword,
                            translation = entry.name.trim(),
                        )
                    }
                }
            }
            return EhTagTranslationDatabase(entries)
        }

        private fun normalize(value: String): String = value.trim().lowercase()
    }
}
