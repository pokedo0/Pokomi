package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
