package tachiyomi.domain.authorSubscription.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuthorSubscriptionTest {

    @Test
    fun `normalizes surrounding and repeated whitespace`() {
        AuthorSubscription.normalizeQuery("  Tatsuki   Fujimoto  ") shouldBe "tatsuki fujimoto"
    }

    @Test
    fun `normalizes mixed case`() {
        AuthorSubscription.normalizeQuery("ONE") shouldBe "one"
    }

    @Test
    fun `keeps non-latin keywords while trimming`() {
        AuthorSubscription.normalizeQuery("  藤本タツキ  ") shouldBe "藤本タツキ"
    }
}
