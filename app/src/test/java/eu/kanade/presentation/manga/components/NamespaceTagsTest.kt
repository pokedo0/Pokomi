package eu.kanade.presentation.manga.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NamespaceTagsTest {

    @Test
    fun `plain source tags become namespace-less display tags`() {
        val chips = SearchMetadataChips(
            meta = null,
            sourceId = 0L,
            tags = listOf("Ahegao ♀", "Demon ♂", "Digital", "Story Arc"),
        )

        chips?.tags?.get("")?.map { it.namespace to it.text to it.search } shouldBe listOf(
            null to "Ahegao ♀" to "Ahegao ♀",
            null to "Demon ♂" to "Demon ♂",
            null to "Digital" to "Digital",
            null to "Story Arc" to "Story Arc",
        )
    }

    @Test
    fun `namespaced and plain source tags use original search values`() {
        val chips = SearchMetadataChips(
            meta = null,
            sourceId = 0L,
            tags = listOf("artist:author", "Ahegao ♀"),
        )

        chips?.tags?.get("artist")?.single()?.search shouldBe "artist:author"
        chips?.tags?.get("")?.single()?.search shouldBe "Ahegao ♀"
    }
}
