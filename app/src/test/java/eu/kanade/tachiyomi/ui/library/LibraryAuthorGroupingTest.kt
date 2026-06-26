package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.translation.EhTagTranslationDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryAuthorGroupMode

class LibraryAuthorGroupingTest {

    @Test
    fun `author groups split authors and artists using yokai separators`() {
        val groups = splitLibraryAuthorNames(
            author = "ONE, 村田雄介 / 赤坂アカ",
            artist = "村田雄介 x 熊之股鍵次 - ONE",
            unknownAuthor = "Unknown",
        )

        assertEquals(
            listOf("ONE", "村田雄介", "赤坂アカ", "熊之股鍵次"),
            groups,
        )
    }

    @Test
    fun `author groups use unknown when author and artist are blank`() {
        val groups = splitLibraryAuthorNames(
            author = " ",
            artist = null,
            unknownAuthor = "Unknown",
        )

        assertEquals(listOf("Unknown"), groups)
    }

    @Test
    fun `artist grouping prefers ehtag artist namespace matches from either field`() {
        val groups = resolveLibraryAuthorGroupNames(
            author = "Circle A / Artist B",
            artist = "Circle C",
            unknownAuthor = "Unknown",
            mode = LibraryAuthorGroupMode.ARTIST,
            tagDatabase = database(
                "artist" to "Artist B",
                "group" to "Circle C",
            ),
        )

        assertEquals(listOf("Artist B"), groups)
    }

    @Test
    fun `artist grouping falls back to artist field when no ehtag artist match exists`() {
        val groups = resolveLibraryAuthorGroupNames(
            author = "Circle A",
            artist = "Artist A",
            unknownAuthor = "Unknown",
            mode = LibraryAuthorGroupMode.ARTIST,
            tagDatabase = EhTagTranslationDatabase.Empty,
        )

        assertEquals(listOf("Artist A"), groups)
    }

    @Test
    fun `artist grouping falls back to author field when artist field is missing`() {
        val groups = resolveLibraryAuthorGroupNames(
            author = "Circle A",
            artist = "Unknown",
            unknownAuthor = "Unknown",
            mode = LibraryAuthorGroupMode.ARTIST,
            tagDatabase = EhTagTranslationDatabase.Empty,
        )

        assertEquals(listOf("Circle A"), groups)
    }

    @Test
    fun `group grouping prefers ehtag group namespace matches from either field`() {
        val groups = resolveLibraryAuthorGroupNames(
            author = "Artist A",
            artist = "Circle B",
            unknownAuthor = "Unknown",
            mode = LibraryAuthorGroupMode.GROUP,
            tagDatabase = database(
                "artist" to "Artist A",
                "group" to "Circle B",
            ),
        )

        assertEquals(listOf("Circle B"), groups)
    }

    @Test
    fun `artist or group grouping combines both namespace strategies`() {
        val groups = resolveLibraryAuthorGroupNames(
            author = "Circle A",
            artist = "Artist A",
            unknownAuthor = "Unknown",
            mode = LibraryAuthorGroupMode.ARTIST_OR_GROUP,
            tagDatabase = EhTagTranslationDatabase.Empty,
        )

        assertEquals(listOf("Artist A", "Circle A"), groups)
    }

    private fun database(vararg tags: Pair<String, String>): EhTagTranslationDatabase {
        return EhTagTranslationDatabase(
            entries = tags.map { (namespace, keyword) ->
                EhTagTranslationDatabase.Entry(
                    namespace = namespace,
                    keyword = keyword,
                    translation = "",
                )
            },
        )
    }
}
