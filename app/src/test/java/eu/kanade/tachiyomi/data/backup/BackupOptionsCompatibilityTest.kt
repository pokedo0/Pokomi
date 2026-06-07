package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupOptionsCompatibilityTest {

    @Test
    fun `backup options decode old arrays with following enabled by default`() {
        val options = BackupOptions.fromBooleanArray(
            booleanArrayOf(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
            ),
        )

        assertTrue(options.following)
    }

    @Test
    fun `backup options decode new following value`() {
        val options = BackupOptions.fromBooleanArray(
            booleanArrayOf(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
            ),
        )

        assertFalse(options.following)
    }

    @Test
    fun `restore options decode old arrays with following enabled by default`() {
        val options = RestoreOptions.fromBooleanArray(
            booleanArrayOf(
                true,
                true,
                true,
                true,
                true,
                true,
            ),
        )

        assertTrue(options.following)
    }

    @Test
    fun `restore options decode new following value`() {
        val options = RestoreOptions.fromBooleanArray(
            booleanArrayOf(
                true,
                true,
                true,
                true,
                true,
                true,
                false,
            ),
        )

        assertFalse(options.following)
    }
}
