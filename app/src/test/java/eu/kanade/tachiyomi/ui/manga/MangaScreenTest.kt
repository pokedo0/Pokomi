package eu.kanade.tachiyomi.ui.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenTest {

    @Test
    fun `global search removes trailing gender symbols for every source`() {
        normalizeGlobalSearchQuery("Big Ass ♀") shouldBe "Big Ass"
        normalizeGlobalSearchQuery("Sole Male ♂♂") shouldBe "Sole Male"
    }

    @Test
    fun `global search keeps queries without trailing gender symbols`() {
        normalizeGlobalSearchQuery("Digital") shouldBe "Digital"
        normalizeGlobalSearchQuery("Big Ass") shouldBe "Big Ass"
    }
}
