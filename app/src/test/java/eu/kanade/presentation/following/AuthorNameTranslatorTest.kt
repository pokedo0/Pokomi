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
