package eu.kanade.tachiyomi.ui.library

fun splitLibraryAuthorNames(
    author: String?,
    artist: String?,
    unknownAuthor: String,
): List<String> {
    val names = listOfNotNull(
        author.takeUnless { it.isNullOrBlank() },
        artist.takeUnless { it.isNullOrBlank() },
    )

    if (names.isEmpty()) {
        return listOf(unknownAuthor)
    }

    return names
        .flatMap { it.split(",", "/", " x ", " - ", ignoreCase = true) }
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .distinct()
}
