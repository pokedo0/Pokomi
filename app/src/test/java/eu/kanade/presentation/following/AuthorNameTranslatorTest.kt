package eu.kanade.presentation.following

import eu.kanade.tachiyomi.data.translation.EhTagTranslationDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class AuthorNameTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `following names preserve namespace while translating keyword`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        translateFollowingName("artist:michiko", database::translateAny, database::translateTag) shouldBe
            "artist:美智子"
        translateFollowingName("group:michiking", database::translateAny, database::translateTag) shouldBe
            "group:米奇王"
        translateFollowingName("tag:michiko", database::translateAny, database::translateTag) shouldBe
            "tag:美智子（红蝶）"
        translateFollowingName("michiko", database::translateAny, database::translateTag) shouldBe
            "美智子（红蝶）"
    }

    @Test
    fun `namespaced author translator distinguishes ambiguous artist and group names`() {
        val database = EhTagTranslationDatabase.parse(json, ambiguousAuthorDatabase)
        val fallback: (String) -> String = { database.translateAuthorOrNamespacedTag(it) ?: it }

        translateAuthorNameWithNamespace("Misaki", "artist", database::translateTag, fallback) shouldBe "御崎"
        translateAuthorNameWithNamespace("Misaki", "group", database::translateTag, fallback) shouldBe "三崎"
        translateAuthorNameWithNamespace("Akutenkou", "artist", database::translateTag, fallback) shouldBe "恶天候"
        translateAuthorNameWithNamespace("Akutenkou", "group", database::translateTag, fallback) shouldBe "恶天候"
    }

    private val ambiguousAuthorDatabase = """
        {
          "data": [
            {
              "namespace": "artist",
              "data": {
                "akutenkou": { "name": "恶天候" },
                "misaki": { "name": "御崎" }
              }
            },
            {
              "namespace": "group",
              "data": {
                "misaki": { "name": "三崎" }
              }
            }
          ]
        }
    """.trimIndent()

    private val sampleDatabase = """
        {
          "data": [
            {
              "namespace": "character",
              "data": {
                "michiko": { "name": "美智子（红蝶）" }
              }
            },
            {
              "namespace": "artist",
              "data": {
                "michiko": { "name": "美智子" }
              }
            },
            {
              "namespace": "group",
              "data": {
                "michiking": { "name": "米奇王" }
              }
            }
          ]
        }
    """.trimIndent()
}
