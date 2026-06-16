package eu.kanade.tachiyomi.ui.following

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.following.FollowingScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.authorSubscription.service.FollowingPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.pkm.PKMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object FollowingTab : Tab {
    private fun readResolve(): Any = FollowingTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val icon = if (isSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
            return TabOptions(
                index = 1u,
                title = stringResource(PKMR.strings.following),
                icon = rememberVectorPainter(icon),
            )
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FollowingScreenModel() }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val autoRefreshOnSwitch = remember {
            Injekt.get<FollowingPreferences>().autoRefreshOnSwitch().get()
        }
        LaunchedEffect(Unit) {
            if (autoRefreshOnSwitch) {
                screenModel.refreshLoaded()
            }
        }
        // KMK <--

        FollowingScreen(
            subscriptions = state.subscriptions,
            results = state.results,
            getManga = screenModel::getManga,
            onClickManga = { manga -> navigator.push(MangaScreen(manga.id, true)) },
            onLongClickManga = { manga -> navigator.push(MangaScreen(manga.id, true)) },
            onPullRefresh = screenModel::refreshLoaded,
            onRefresh = screenModel::refresh,
            onRefreshAll = screenModel::refreshAll,
            onOpenSearch = { query -> navigator.push(GlobalSearchScreen(query)) },
            onRankAuthors = { anchorId ->
                screenModel.onAuthorRankOpened()
                navigator.push(AuthorRankScreen(anchorId, screenModel::onAuthorRankSaved))
            },
            onVisible = screenModel::loadVisible,
            pendingRankAnchorId = state.pendingRankAnchorId,
            pendingRankOrderSnapshot = state.pendingRankOrderSnapshot,
            highlightedAuthorId = state.highlightedAuthorId,
            onRankAnchorShown = screenModel::onAuthorRankAnchorShown,
            onHighlightConsumed = screenModel::clearAuthorRankHighlight,
        )
    }
}
