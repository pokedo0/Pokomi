package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.translation.EhTagTranslationDatabase
import tachiyomi.domain.library.model.LibraryAuthorGroupMode

data class ResolvedLibraryAuthorGroup(
    val name: String,
    val namespace: String?,
)

fun splitLibraryAuthorNames(
    author: String?,
    artist: String?,
    unknownAuthor: String,
): List<String> {
    val names = splitAuthorFieldNames(author, artist, unknownAuthor = unknownAuthor)
    return if (names.isEmpty()) {
        listOf(unknownAuthor)
    } else {
        names
    }
}

fun resolveLibraryAuthorGroupNames(
    author: String?,
    artist: String?,
    unknownAuthor: String,
    mode: LibraryAuthorGroupMode,
    tagDatabase: EhTagTranslationDatabase,
): List<String> {
    return resolveLibraryAuthorGroups(
        author = author,
        artist = artist,
        unknownAuthor = unknownAuthor,
        mode = mode,
        tagDatabase = tagDatabase,
    ).map { it.name }.distinct()
}

fun resolveLibraryAuthorGroups(
    author: String?,
    artist: String?,
    unknownAuthor: String,
    mode: LibraryAuthorGroupMode,
    tagDatabase: EhTagTranslationDatabase,
): List<ResolvedLibraryAuthorGroup> {
    val artistNames = splitAuthorFieldNames(artist, unknownAuthor = unknownAuthor)
    val authorNames = splitAuthorFieldNames(author, unknownAuthor = unknownAuthor)

    return when (mode) {
        LibraryAuthorGroupMode.ARTIST -> resolveByNamespace(
            namespace = ARTIST_NAMESPACE,
            otherNamespace = GROUP_NAMESPACE,
            primaryNames = artistNames,
            fallbackNames = authorNames,
            unknownAuthor = unknownAuthor,
            tagDatabase = tagDatabase,
        ).map { name ->
            ResolvedLibraryAuthorGroup(
                name = name,
                namespace = ARTIST_NAMESPACE.takeUnless { name.equals(unknownAuthor, ignoreCase = true) },
            )
        }
        LibraryAuthorGroupMode.GROUP -> resolveByNamespace(
            namespace = GROUP_NAMESPACE,
            otherNamespace = ARTIST_NAMESPACE,
            primaryNames = authorNames,
            fallbackNames = artistNames,
            unknownAuthor = unknownAuthor,
            tagDatabase = tagDatabase,
        ).map { name ->
            ResolvedLibraryAuthorGroup(
                name = name,
                namespace = GROUP_NAMESPACE.takeUnless { name.equals(unknownAuthor, ignoreCase = true) },
            )
        }
        LibraryAuthorGroupMode.ARTIST_OR_GROUP -> {
            val resolvedNames = (
                resolveByNamespace(
                    namespace = ARTIST_NAMESPACE,
                    otherNamespace = GROUP_NAMESPACE,
                    primaryNames = artistNames,
                    fallbackNames = authorNames,
                    unknownAuthor = unknownAuthor,
                    tagDatabase = tagDatabase,
                ) +
                    resolveByNamespace(
                        namespace = GROUP_NAMESPACE,
                        otherNamespace = ARTIST_NAMESPACE,
                        primaryNames = authorNames,
                        fallbackNames = artistNames,
                        unknownAuthor = unknownAuthor,
                        tagDatabase = tagDatabase,
                    )
                ).distinct()

            resolvedNames.map { name ->
                val namespace = if (name.equals(unknownAuthor, ignoreCase = true)) {
                    null
                } else {
                    resolveAuthorEntryNamespace(
                        name = name,
                        artistNames = artistNames,
                        authorNames = authorNames,
                        tagDatabase = tagDatabase,
                    )
                }
                ResolvedLibraryAuthorGroup(name = name, namespace = namespace)
            }
        }
    }
}

fun resolveMangaAuthorFieldNamespace(
    name: String,
    author: String?,
    artist: String?,
    unknownAuthor: String,
    tagDatabase: EhTagTranslationDatabase,
): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.equals(unknownAuthor, ignoreCase = true)) return null
    val artistNames = splitAuthorFieldNames(artist, unknownAuthor = unknownAuthor)
    val authorNames = splitAuthorFieldNames(author, unknownAuthor = unknownAuthor)
    return resolveAuthorEntryNamespace(
        name = trimmed,
        artistNames = artistNames,
        authorNames = authorNames,
        tagDatabase = tagDatabase,
    )
}

internal fun resolveAuthorEntryNamespace(
    name: String,
    artistNames: List<String>,
    authorNames: List<String>,
    tagDatabase: EhTagTranslationDatabase,
): String? {
    val inArtist = tagDatabase.containsTag(ARTIST_NAMESPACE, name)
    val inGroup = tagDatabase.containsTag(GROUP_NAMESPACE, name)

    return when {
        inArtist && !inGroup -> ARTIST_NAMESPACE
        inGroup && !inArtist -> GROUP_NAMESPACE
        inArtist && inGroup -> {
            val inArtistNames = artistNames.any { it.equals(name, ignoreCase = true) }
            val inAuthorNames = authorNames.any { it.equals(name, ignoreCase = true) }
            val referenceNames = when {
                inArtistNames && !inAuthorNames -> authorNames.ifEmpty {
                    artistNames.filterNot { it.equals(name, ignoreCase = true) }
                }
                inAuthorNames && !inArtistNames -> artistNames.ifEmpty {
                    authorNames.filterNot { it.equals(name, ignoreCase = true) }
                }
                else -> (artistNames + authorNames).filterNot { it.equals(name, ignoreCase = true) }
            }

            val hasExclusiveArtist = referenceNames.any {
                tagDatabase.containsTag(ARTIST_NAMESPACE, it) && !tagDatabase.containsTag(GROUP_NAMESPACE, it)
            }
            val hasExclusiveGroup = referenceNames.any {
                tagDatabase.containsTag(GROUP_NAMESPACE, it) && !tagDatabase.containsTag(ARTIST_NAMESPACE, it)
            }

            when {
                hasExclusiveArtist && !hasExclusiveGroup -> GROUP_NAMESPACE
                hasExclusiveGroup && !hasExclusiveArtist -> ARTIST_NAMESPACE
                inArtistNames && !inAuthorNames -> ARTIST_NAMESPACE
                inAuthorNames && !inArtistNames -> GROUP_NAMESPACE
                else -> null
            }
        }
        else -> null
    }
}

private fun resolveByNamespace(
    namespace: String,
    otherNamespace: String,
    primaryNames: List<String>,
    fallbackNames: List<String>,
    unknownAuthor: String,
    tagDatabase: EhTagTranslationDatabase,
): List<String> {
    fun isExclusiveTarget(name: String): Boolean =
        tagDatabase.containsTag(namespace, name) && !tagDatabase.containsTag(otherNamespace, name)

    fun isExclusiveOther(name: String): Boolean =
        tagDatabase.containsTag(otherNamespace, name) && !tagDatabase.containsTag(namespace, name)

    fun isAmbiguous(name: String): Boolean =
        tagDatabase.containsTag(namespace, name) && tagDatabase.containsTag(otherNamespace, name)

    // Tier 1: Exclusive target namespace matches win (never pull ambiguous names from fallbackNames).
    val primaryExclusive = primaryNames.filter(::isExclusiveTarget)
    val fallbackExclusive = fallbackNames.filter(::isExclusiveTarget)
    if (primaryExclusive.isNotEmpty()) {
        return (primaryNames.filter { tagDatabase.containsTag(namespace, it) } + fallbackExclusive).distinct()
    }
    if (fallbackExclusive.isNotEmpty()) {
        return fallbackExclusive.distinct()
    }

    // Tier 2: No exclusive target match; respect primaryNames (ambiguous first, then non-exclusive-other).
    val primaryAmbiguous = primaryNames.filter(::isAmbiguous)
    if (primaryAmbiguous.isNotEmpty()) {
        return primaryAmbiguous.distinct()
    }
    val primaryNonExclusiveOther = primaryNames.filterNot(::isExclusiveOther)
    if (primaryNonExclusiveOther.isNotEmpty()) {
        return primaryNonExclusiveOther.distinct()
    }

    // Tier 3: primaryNames is empty or exclusively belongs to otherNamespace (e.g. swapped fields).
    val fallbackMatches = fallbackNames.filter { tagDatabase.containsTag(namespace, it) }
    if (fallbackMatches.isNotEmpty()) {
        return fallbackMatches.distinct()
    }

    // Tier 4: Fallback when database is empty or contains no relevant tags.
    return primaryNames.ifEmpty { fallbackNames }.ifEmpty { listOf(unknownAuthor) }
}

private fun splitAuthorFieldNames(
    vararg values: String?,
    unknownAuthor: String,
): List<String> {
    val names = values
        .mapNotNull { it?.takeUnless { value -> value.isBlank() || value.equals(unknownAuthor, ignoreCase = true) } }

    if (names.isEmpty()) {
        return emptyList()
    }

    return names
        .flatMap { it.split(",", "/", " x ", " - ", ignoreCase = true) }
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .filterNot { it.equals(unknownAuthor, ignoreCase = true) }
        .distinct()
}

internal const val ARTIST_NAMESPACE = "artist"
internal const val GROUP_NAMESPACE = "group"
