package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.pkm.PKMR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
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
        extensionStores,
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
            extensionStores ||
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
                label = MR.strings.extensionStores,
                getter = RestoreOptions::extensionStores,
                setter = { options, enabled -> options.copy(extensionStores = enabled) },
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
                label = PKMR.strings.following,
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
                extensionStores = array.getOrElse(3) { default.extensionStores },
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
