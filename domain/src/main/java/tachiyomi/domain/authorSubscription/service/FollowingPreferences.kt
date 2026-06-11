package tachiyomi.domain.authorSubscription.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class FollowingPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun autoLoadAll() = preferenceStore.getBoolean(
        "pref_following_auto_load_all",
        false,
    )

    fun cacheTtlHours() = preferenceStore.getString(
        "pref_following_cache_ttl_hours",
        "24",
    )

    fun autoRefreshOnSwitch() = preferenceStore.getBoolean(
        "pref_following_auto_refresh_on_switch",
        false,
    )

    fun lastModifiedAt() = preferenceStore.getLong(
        Preference.appStateKey("pref_following_last_modified_at"),
        0,
    )
}
