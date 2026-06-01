package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.GlobalSearchScreen
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.authorSubscription.interactor.DeleteAuthorSubscription
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.UpsertAuthorSubscription
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1)
        }
        // KMK -->
        val scope = rememberCoroutineScope()
        val getAuthorSubscriptions = remember { Injekt.get<GetAuthorSubscriptions>() }
        val upsertAuthorSubscription = remember { Injekt.get<UpsertAuthorSubscription>() }
        val deleteAuthorSubscription = remember { Injekt.get<DeleteAuthorSubscription>() }
        val authorSubscriptions by getAuthorSubscriptions.subscribeAll().collectAsState(initial = emptyList())
        var pendingUnsubscribeQuery by remember { mutableStateOf<String?>(null) }
        val normalizedSearchQuery = state.searchQuery?.let { AuthorSubscription.normalizeQuery(it) }
        val activeSubscription = authorSubscriptions.firstOrNull {
            it.normalizedQuery == normalizedSearchQuery
        }
        // KMK <--

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    SearchItemResult.Loading -> return@LaunchedEffect
                    is SearchItemResult.Success -> {
                        val manga = result.result.singleOrNull()
                        if (manga != null) {
                            navigator.replace(MangaScreen(manga.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            GlobalSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getManga = { screenModel.getManga(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseSourceScreen(it.id, state.searchQuery))
                },
                // KMK -->
                subscribedSourceId = activeSubscription?.source,
                onClickSubscribeSource = { source ->
                    val query = state.searchQuery?.trim().orEmpty()
                    if (query.isNotBlank()) {
                        if (activeSubscription?.source == source.id) {
                            pendingUnsubscribeQuery = query
                        } else {
                            scope.launchIO {
                                upsertAuthorSubscription.await(source = source.id, query = query)
                            }
                        }
                    }
                },
                // KMK <--
                onClickItem = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                onLongClickItem = { manga ->
                    // KMK -->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        // KMK <--
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                // KMK -->
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                hasPinnedSources = screenModel.hasPinnedSources(),
                // KMK <--
            )
        }

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--

        // KMK -->
        if (pendingUnsubscribeQuery != null) {
            AlertDialog(
                onDismissRequest = { pendingUnsubscribeQuery = null },
                title = { Text(text = stringResource(KMR.strings.following_unsubscribe_author)) },
                text = { Text(text = stringResource(KMR.strings.following_unsubscribe_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val query = pendingUnsubscribeQuery ?: return@TextButton
                            pendingUnsubscribeQuery = null
                            scope.launchIO {
                                deleteAuthorSubscription.awaitByQuery(query)
                            }
                        },
                    ) {
                        Text(text = stringResource(KMR.strings.following_unsubscribe_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUnsubscribeQuery = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        // KMK <--
    }
}
