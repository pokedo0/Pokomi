package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    // SY -->
    val savedSearchesFeeds: Boolean = true,
    // SY <--
    // KMK -->
    val following: Boolean = true,
    // KMK <--
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        appSettings,
        extensionRepoSettings,
        sourceSettings,
        // SY -->
        savedSearchesFeeds,
        // SY <--
        // KMK -->
        following,
        // KMK <--
    )

    fun canRestore() =
        libraryEntries ||
            categories ||
            appSettings ||
            extensionRepoSettings ||
            sourceSettings /* SY --> */ ||
            savedSearchesFeeds /* SY <-- */ /* KMK --> */ ||
            following /* KMK <-- */

    companion object {
        val options = persistentListOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.categories,
                getter = RestoreOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = RestoreOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            // SY -->
            Entry(
                // KMK-->
                label = KMR.strings.saved_searches_feeds,
                // KMK <--
                getter = RestoreOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
            // SY <--
            // KMK -->
            Entry(
                label = KMR.strings.following,
                getter = RestoreOptions::following,
                setter = { options, enabled -> options.copy(following = enabled) },
            ),
            // KMK <--
        )

        fun fromBooleanArray(array: BooleanArray): RestoreOptions {
            val default = RestoreOptions()
            return RestoreOptions(
                libraryEntries = array.getOrElse(0) { default.libraryEntries },
                categories = array.getOrElse(1) { default.categories },
                appSettings = array.getOrElse(2) { default.appSettings },
                extensionRepoSettings = array.getOrElse(3) { default.extensionRepoSettings },
                sourceSettings = array.getOrElse(4) { default.sourceSettings },
                // SY -->
                savedSearchesFeeds = array.getOrElse(5) { default.savedSearchesFeeds },
                // SY <--
                // KMK -->
                following = array.getOrElse(6) { default.following },
                // KMK <--
            )
        }
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
    )
}
