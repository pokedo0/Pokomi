package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Translates romanized author/group names and provides tag suggestions using
 * the EhTagTranslation database.
 *
 * Mirrors EhViewer's approach: download the upstream JSON, cache parsed tag
 * data on disk, and refresh with a SHA fingerprint + daily throttle. All
 * operations fail silently and fall back to the original name/no suggestions.
 */
class AuthorTagTranslator(
    context: Context,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {
    private val dir = File(context.filesDir, DIR_NAME)
    private val dataFile = File(dir, DATA_FILE_NAME)
    private val shaFile = File(dir, SHA_FILE_NAME)

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // KMK -->
    private val databaseFlow = MutableStateFlow(EhTagTranslationDatabase.Empty)
    private val translationsFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    val database: StateFlow<EhTagTranslationDatabase> = databaseFlow.asStateFlow()

    /** Observable translation map; emits a new value once the database is loaded/updated. */
    val translations: StateFlow<Map<String, String>> = translationsFlow.asStateFlow()
    // KMK <--

    /** Returns the translated name, or [name] unchanged when no translation exists. */
    fun translate(name: String): String = databaseFlow.value.translateAuthor(name) ?: name

    fun suggest(query: String, includeTranslations: Boolean, limit: Int = 15): List<TagSuggestion> {
        return databaseFlow.value.suggest(query, includeTranslations, limit)
    }

    /** Loads the cached database and refreshes it in the background (fire-and-forget). */
    fun launchUpdate() {
        scope.launch { update() }
    }

    private suspend fun update() {
        mutex.withLock {
            // Load the on-disk cache first so translations are available immediately.
            var cacheLoaded = false
            var shouldForceRefresh = false
            if (databaseFlow.value.entries.isEmpty() && dataFile.exists()) {
                runCatching { json.decodeFromString<EhTagTranslationDatabase>(dataFile.readText()) }
                    .onSuccess {
                        setDatabase(it)
                        cacheLoaded = true
                    }
                    .onFailure {
                        shouldForceRefresh = true
                        logcat(LogPriority.WARN, it) { "Failed to read cached author translations" }
                    }
            }

            // Daily throttle: skip the network entirely if refreshed within the last day.
            if (cacheLoaded && System.currentTimeMillis() - dataFile.lastModified() < UPDATE_INTERVAL_MS) {
                return
            }

            try {
                val remoteSha = fetch(SHA_URL).trim()
                val localSha = shaFile.takeUnless { shouldForceRefresh }?.takeIf { it.exists() }?.readText()?.trim()
                if (remoteSha.isNotEmpty() && remoteSha == localSha) {
                    // Up to date; touch the data file to reset the daily throttle.
                    dataFile.setLastModified(System.currentTimeMillis())
                    return
                }

                val parsed = EhTagTranslationDatabase.parse(json, fetch(DATA_URL))
                if (parsed.entries.isEmpty()) return

                dir.mkdirs()
                dataFile.writeText(json.encodeToString(parsed))
                if (remoteSha.isNotEmpty()) shaFile.writeText(remoteSha)
                setDatabase(parsed)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update author translations" }
            }
        }
    }

    private suspend fun fetch(url: String): String =
        networkHelper.client.newCall(GET(url)).awaitSuccess().use { it.body.string() }

    private fun setDatabase(database: EhTagTranslationDatabase) {
        databaseFlow.value = database
        translationsFlow.value = database.authorTranslations
    }

    companion object {
        private const val DIR_NAME = "author-translations"
        private const val DATA_FILE_NAME = "author-translations.json"
        private const val SHA_FILE_NAME = "author-translations.sha"

        private const val DATA_URL =
            "https://raw.githubusercontent.com/EhTagTranslation/DatabaseReleases/master/db.text.json"
        private const val SHA_URL =
            "https://raw.githubusercontent.com/EhTagTranslation/DatabaseReleases/master/sha"
        private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
