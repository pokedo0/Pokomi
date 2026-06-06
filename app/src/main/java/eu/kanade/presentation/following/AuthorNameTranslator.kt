package eu.kanade.presentation.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.translation.AuthorTagTranslator
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a function that maps a romanized author name to its localized name
 * when the "translate author names" preference is on. Falls back to the
 * original name when the feature is off or no translation exists.
 *
 * The returned lambda is recomputed when the toggle flips or the translation
 * database finishes loading, so screens recompose with fresh names.
 */
@Composable
fun rememberAuthorNameTranslator(): (String) -> String {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val translator = remember { Injekt.get<AuthorTagTranslator>() }

    val enabled by uiPreferences.translateAuthorNames().collectAsState()
    val translations by translator.translations.collectAsState()

    return remember(enabled, translations) {
        { name -> if (enabled) translator.translate(name) else name }
    }
}
