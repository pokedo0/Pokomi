package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class EhTagTranslationSuggesterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `suggestions include all namespaces without namespace prefixes`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.suggest("michi", includeTranslations = true, limit = 10)
            .map { it.keyword } shouldContainExactly listOf(
            "michiko",
            "michiyon",
            "michishio",
            "michiking",
        )
    }

    @Test
    fun `suggestions expose translation hints when enabled`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.suggest("michi", includeTranslations = true, limit = 1)
            .single() shouldBe TagSuggestion(
            keyword = "michiko",
            hint = "美智子（红蝶）",
        )
    }

    @Test
    fun `suggestions can match translation text when enabled`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.suggest("红蝶", includeTranslations = true, limit = 10)
            .map { it.keyword } shouldContainExactly listOf("michiko")
    }

    @Test
    fun `suggestions ignore translation text and hints when disabled`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.suggest("红蝶", includeTranslations = false, limit = 10) shouldBe emptyList()
        database.suggest("michi", includeTranslations = false, limit = 1)
            .single() shouldBe TagSuggestion(
            keyword = "michiko",
            hint = null,
        )
    }

    @Test
    fun `suggestions deduplicate identical keywords across namespaces`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.suggest("michiko", includeTranslations = true, limit = 10)
            .map { it.keyword } shouldContainExactly listOf("michiko")
    }

    @Test
    fun `author translations keep artist and group namespaces only`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.authorTranslations shouldBe mapOf(
            "michiyon" to "みちょん",
            "michiko" to "美智子",
            "michiking" to "米奇王",
        )
    }

    @Test
    fun `namespaced tag translations keep namespace and translate keyword`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.translateAuthorOrNamespacedTag("artist:michiyon") shouldBe "artist:みちょん"
        database.translateAuthorOrNamespacedTag("character:michiko") shouldBe "character:美智子（红蝶）"
    }

    @Test
    fun `namespaced tag translations ignore unknown namespaces and missing keywords`() {
        val database = EhTagTranslationDatabase.parse(json, sampleDatabase)

        database.translateAuthorOrNamespacedTag("unknown:michiko") shouldBe null
        database.translateAuthorOrNamespacedTag("artist:missing") shouldBe null
        database.translateAuthorOrNamespacedTag("artist:") shouldBe null
    }

    private val sampleDatabase = """
        {
          "data": [
            {
              "namespace": "character",
              "data": {
                "michiko": { "name": "美智子（红蝶）" },
                "michishio": { "name": "满潮(槁)" }
              }
            },
            {
              "namespace": "artist",
              "data": {
                "michiyon": { "name": "みちょん" },
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
