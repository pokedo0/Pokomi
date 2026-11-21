package eu.kanade.presentation.manga.components

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PagePreviewsTest {

    @Test
    fun `custom column count overrides automatic width calculation`() {
        assertEquals(2, resolvePagePreviewColumnCount(maxWidth = 360.dp, preferredColumns = 2))
        assertEquals(5, resolvePagePreviewColumnCount(maxWidth = 360.dp, preferredColumns = 5))
        assertEquals(1, resolvePagePreviewColumnCount(maxWidth = 800.dp, preferredColumns = 1))
    }

    @Test
    fun `zero preferred columns falls back to width divided by 120dp with minimum 1`() {
        assertEquals(3, resolvePagePreviewColumnCount(maxWidth = 360.dp, preferredColumns = 0))
        assertEquals(3, resolvePagePreviewColumnCount(maxWidth = 411.dp, preferredColumns = 0))
        assertEquals(6, resolvePagePreviewColumnCount(maxWidth = 720.dp, preferredColumns = 0))
        assertEquals(1, resolvePagePreviewColumnCount(maxWidth = 80.dp, preferredColumns = 0))
    }
}
