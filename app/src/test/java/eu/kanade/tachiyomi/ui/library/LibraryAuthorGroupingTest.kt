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

    @Test
    fun `exclusive artist disambiguates ambiguous group name in author field`() {
        val db = database(
            "artist" to "Akutenkou",
            "artist" to "Misaki",
            "group" to "Misaki",
        )

        assertEquals(
            listOf(ResolvedLibraryAuthorGroup("Akutenkou", "artist")),
            resolveLibraryAuthorGroups(
                author = "Misaki",
                artist = "Akutenkou",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST,
                tagDatabase = db,
            ),
        )
        assertEquals(
            listOf(ResolvedLibraryAuthorGroup("Misaki", "group")),
            resolveLibraryAuthorGroups(
                author = "Misaki",
                artist = "Akutenkou",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.GROUP,
                tagDatabase = db,
            ),
        )
        assertEquals(
            listOf(
                ResolvedLibraryAuthorGroup("Akutenkou", "artist"),
                ResolvedLibraryAuthorGroup("Misaki", "group"),
            ),
            resolveLibraryAuthorGroups(
                author = "Misaki",
                artist = "Akutenkou",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST_OR_GROUP,
                tagDatabase = db,
            ),
        )
    }

    @Test
    fun `swapped exclusive artist and ambiguous name still resolve excluded namespaces`() {
        val db = database(
            "artist" to "Akutenkou",
            "artist" to "Misaki",
            "group" to "Misaki",
        )

        assertEquals(
            listOf(ResolvedLibraryAuthorGroup("Akutenkou", "artist")),
            resolveLibraryAuthorGroups(
                author = "Akutenkou",
                artist = "Misaki",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST,
                tagDatabase = db,
            ),
        )
        assertEquals(
            listOf(ResolvedLibraryAuthorGroup("Misaki", "group")),
            resolveLibraryAuthorGroups(
                author = "Akutenkou",
                artist = "Misaki",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.GROUP,
                tagDatabase = db,
            ),
        )
        assertEquals(
            listOf(
                ResolvedLibraryAuthorGroup("Akutenkou", "artist"),
                ResolvedLibraryAuthorGroup("Misaki", "group"),
            ),
            resolveLibraryAuthorGroups(
                author = "Akutenkou",
                artist = "Misaki",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST_OR_GROUP,
                tagDatabase = db,
            ),
        )
    }

    @Test
    fun `exclusive group disambiguates ambiguous artist name even when fields are swapped`() {
        val db = database(
            "group" to "Circle X",
            "artist" to "Misaki",
            "group" to "Misaki",
        )

        assertEquals(
            listOf(
                ResolvedLibraryAuthorGroup("Misaki", "artist"),
                ResolvedLibraryAuthorGroup("Circle X", "group"),
            ),
            resolveLibraryAuthorGroups(
                author = "Misaki",
                artist = "Circle X",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST_OR_GROUP,
                tagDatabase = db,
            ),
        )
    }

    @Test
    fun `unlisted artist is preserved instead of pulling ambiguous group from author field`() {
        val db = database(
            "artist" to "Misaki",
            "group" to "Misaki",
        )

        assertEquals(
            listOf("NewArtist"),
            resolveLibraryAuthorGroupNames(
                author = "Misaki",
                artist = "NewArtist",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST,
                tagDatabase = db,
            ),
        )
        assertEquals(
            listOf("Misaki"),
            resolveLibraryAuthorGroupNames(
                author = "Misaki",
                artist = "NewArtist",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.GROUP,
                tagDatabase = db,
            ),
        )
    }

    @Test
    fun `multi artist with ambiguous co-artist keeps both artists and resolves namespaces`() {
        val db = database(
            "artist" to "Akutenkou",
            "artist" to "Misaki",
            "group" to "Misaki",
            "group" to "Circle X",
        )

        assertEquals(
            listOf("Akutenkou", "Misaki"),
            resolveLibraryAuthorGroupNames(
                author = "Circle X",
                artist = "Akutenkou, Misaki",
                unknownAuthor = "Unknown",
                mode = LibraryAuthorGroupMode.ARTIST,
                tagDatabase = db,
            ),
        )
        assertEquals(
            "artist",
            resolveMangaAuthorFieldNamespace(
                name = "Misaki",
                author = "Circle X",
                artist = "Akutenkou, Misaki",
                unknownAuthor = "Unknown",
                tagDatabase = db,
            ),
        )
    }

    @Test
    fun `manga detail author field namespace resolution handles normal swapped and ambiguous pairs`() {
        val db = database(
            "artist" to "Akutenkou",
            "artist" to "Misaki",
            "group" to "Misaki",
            "artist" to "Sakura",
            "group" to "Sakura",
            "group" to "Circle X",
        )

        assertEquals(
            "group",
            resolveMangaAuthorFieldNamespace("Misaki", author = "Misaki", artist = "Akutenkou", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "artist",
            resolveMangaAuthorFieldNamespace("Akutenkou", author = "Misaki", artist = "Akutenkou", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "group",
            resolveMangaAuthorFieldNamespace("Misaki", author = "Akutenkou", artist = "Misaki", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "artist",
            resolveMangaAuthorFieldNamespace("Misaki", author = "Circle X", artist = "Misaki", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "artist",
            resolveMangaAuthorFieldNamespace("Misaki", author = "Misaki", artist = "Circle X", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "group",
            resolveMangaAuthorFieldNamespace("Misaki", author = "Misaki", artist = "Sakura", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            "artist",
            resolveMangaAuthorFieldNamespace("Sakura", author = "Misaki", artist = "Sakura", unknownAuthor = "Unknown", tagDatabase = db),
        )
        assertEquals(
            null,
            resolveMangaAuthorFieldNamespace("Misaki", author = "Misaki", artist = "Misaki", unknownAuthor = "Unknown", tagDatabase = db),
        )
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
