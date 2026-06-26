package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.translation.EhTagTranslationDatabase
import tachiyomi.domain.library.model.LibraryAuthorGroupMode

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
    val artistNames = splitAuthorFieldNames(artist, unknownAuthor = unknownAuthor)
    val authorNames = splitAuthorFieldNames(author, unknownAuthor = unknownAuthor)

    return when (mode) {
        LibraryAuthorGroupMode.ARTIST -> resolveByNamespace(
            namespace = ARTIST_NAMESPACE,
            primaryNames = artistNames,
            fallbackNames = authorNames,
            unknownAuthor = unknownAuthor,
            tagDatabase = tagDatabase,
        )
        LibraryAuthorGroupMode.GROUP -> resolveByNamespace(
            namespace = GROUP_NAMESPACE,
            primaryNames = authorNames,
            fallbackNames = artistNames,
            unknownAuthor = unknownAuthor,
            tagDatabase = tagDatabase,
        )
        LibraryAuthorGroupMode.ARTIST_OR_GROUP -> (
            resolveByNamespace(
                namespace = ARTIST_NAMESPACE,
                primaryNames = artistNames,
                fallbackNames = authorNames,
                unknownAuthor = unknownAuthor,
                tagDatabase = tagDatabase,
            ) +
                resolveByNamespace(
                    namespace = GROUP_NAMESPACE,
                    primaryNames = authorNames,
                    fallbackNames = artistNames,
                    unknownAuthor = unknownAuthor,
                    tagDatabase = tagDatabase,
                )
            ).distinct()
    }
}

private fun resolveByNamespace(
    namespace: String,
    primaryNames: List<String>,
    fallbackNames: List<String>,
    unknownAuthor: String,
    tagDatabase: EhTagTranslationDatabase,
): List<String> {
    val names = primaryNames + fallbackNames
    val directMatches = names
        .filter { tagDatabase.containsTag(namespace, it) }
        .distinct()

    return when {
        directMatches.isNotEmpty() -> directMatches
        primaryNames.isNotEmpty() -> primaryNames
        fallbackNames.isNotEmpty() -> fallbackNames
        else -> listOf(unknownAuthor)
    }
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

private const val ARTIST_NAMESPACE = "artist"
private const val GROUP_NAMESPACE = "group"
