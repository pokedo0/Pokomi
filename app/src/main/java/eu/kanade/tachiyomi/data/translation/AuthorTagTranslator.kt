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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Translates romanized author/group names into localized names using the
 * EhTagTranslation database (namespaces "artist" and "group" only).
 *
 * Mirrors EhViewer's approach: download the upstream JSON, keep only the
 * translated [TagEntry.name], cache a flattened map on disk, and refresh with a
 * SHA fingerprint + daily throttle. All operations fail silently and fall back
 * to the original name.
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
    private val translationsFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Observable translation map; emits a new value once the database is loaded/updated. */
    val translations: StateFlow<Map<String, String>> = translationsFlow.asStateFlow()
    // KMK <--

    /** Returns the translated name, or [name] unchanged when no translation exists. */
    fun translate(name: String): String = translationsFlow.value[normalize(name)] ?: name

    private fun normalize(name: String): String = name.trim().lowercase()

    /** Loads the cached database and refreshes it in the background (fire-and-forget). */
    fun launchUpdate() {
        scope.launch { update() }
    }

    private suspend fun update() = mutex.withLock {
        // Load the on-disk cache first so translations are available immediately.
        if (translationsFlow.value.isEmpty() && dataFile.exists()) {
            runCatching { json.decodeFromString<Map<String, String>>(dataFile.readText()) }
                .onSuccess { translationsFlow.value = it }
                .onFailure { logcat(LogPriority.WARN, it) { "Failed to read cached author translations" } }
        }

        // Daily throttle: skip the network entirely if refreshed within the last day.
        if (dataFile.exists() && System.currentTimeMillis() - dataFile.lastModified() < UPDATE_INTERVAL_MS) {
            return
        }

        try {
            val remoteSha = fetch(SHA_URL).trim()
            val localSha = shaFile.takeIf { it.exists() }?.readText()?.trim()
            if (remoteSha.isNotEmpty() && remoteSha == localSha) {
                // Up to date; touch the data file to reset the daily throttle.
                dataFile.setLastModified(System.currentTimeMillis())
                return
            }

            val parsed = parseDatabase(fetch(DATA_URL))
            if (parsed.isEmpty()) return

            dir.mkdirs()
            dataFile.writeText(json.encodeToString(parsed))
            if (remoteSha.isNotEmpty()) shaFile.writeText(remoteSha)
            translationsFlow.value = parsed
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to update author translations" }
        }
    }

    private suspend fun fetch(url: String): String =
        networkHelper.client.newCall(GET(url)).awaitSuccess().use { it.body.string() }

    /** Parses the EhTagTranslation JSON, keeping only artist + group names. */
    private fun parseDatabase(raw: String): Map<String, String> {
        val database = json.decodeFromString<TagDatabase>(raw)
        val result = HashMap<String, String>()
        database.data
            .filter { it.namespace in TRANSLATED_NAMESPACES }
            .forEach { namespace ->
                namespace.data.forEach { (original, entry) ->
                    val name = entry.name.trim()
                    if (name.isNotEmpty()) result[normalize(original)] = name
                }
            }
        return result
    }

    @Serializable
    private data class TagDatabase(val data: List<NamespaceData> = emptyList())

    @Serializable
    private data class NamespaceData(
        val namespace: String = "",
        val data: Map<String, TagEntry> = emptyMap(),
    )

    @Serializable
    private data class TagEntry(val name: String = "")

    companion object {
        private const val DIR_NAME = "author-translations"
        private const val DATA_FILE_NAME = "author-translations.json"
        private const val SHA_FILE_NAME = "author-translations.sha"

        private const val DATA_URL =
            "https://raw.githubusercontent.com/EhTagTranslation/DatabaseReleases/master/db.text.json"
        private const val SHA_URL =
            "https://raw.githubusercontent.com/EhTagTranslation/DatabaseReleases/master/sha"

        private val TRANSLATED_NAMESPACES = setOf("artist", "group")
        private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
