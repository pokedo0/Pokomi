package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsFollowingScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsFollowingScreen

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = KMR.strings.pref_category_following

    @Composable
    override fun getPreferences(): List<Preference> {
        val followingPreferences = remember { Injekt.get<FollowingPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = followingPreferences.autoLoadAll(),
                title = stringResource(KMR.strings.pref_following_auto_load_all),
                subtitle = stringResource(KMR.strings.pref_following_auto_load_all_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = followingPreferences.autoRefreshOnSwitch(),
                title = stringResource(KMR.strings.pref_following_auto_refresh_on_switch),
                subtitle = stringResource(KMR.strings.pref_following_auto_refresh_on_switch_summary),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = followingPreferences.cacheTtlHours(),
                title = stringResource(KMR.strings.pref_following_cache_ttl),
                subtitle = stringResource(KMR.strings.pref_following_cache_ttl_summary),
                onValueChanged = { newValue ->
                    newValue.isBlank() || newValue.trim().toIntOrNull()?.let { it >= 0 } == true
                },
            ),
        )
    }
}
