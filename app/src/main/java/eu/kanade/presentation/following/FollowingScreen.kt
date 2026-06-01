package eu.kanade.presentation.following

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.CollapsibleAuthorHeader
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun FollowingScreen(
    subscriptions: List<AuthorSubscription>,
    results: Map<Long, SearchItemResult>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onPullRefresh: () -> Unit,
    onVisible: (Long) -> Unit,
) {
    val isRefreshing = results.values.any { it is SearchItemResult.Loading }
    var collapsedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val collapsedIdSet = remember(collapsedIds) { collapsedIds.toSet() }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.following),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        PullRefresh(
            refreshing = isRefreshing,
            enabled = subscriptions.isNotEmpty(),
            onRefresh = onPullRefresh,
            indicatorPadding = paddingValues,
        ) {
            if (subscriptions.isEmpty()) {
                EmptyScreen(
                    stringRes = KMR.strings.following_empty,
                    modifier = Modifier.padding(paddingValues),
                )
            } else {
                LazyColumn(contentPadding = paddingValues) {
                    items(subscriptions, key = { it.id }) { subscription ->
                        val expanded = subscription.id !in collapsedIdSet
                        FollowingAuthorSection(
                            subscription = subscription,
                            result = results[subscription.id],
                            expanded = expanded,
                            getManga = getManga,
                            onClickManga = onClickManga,
                            onLongClickManga = onLongClickManga,
                            onToggleExpanded = {
                                collapsedIds = if (expanded) {
                                    collapsedIds + subscription.id
                                } else {
                                    collapsedIds - subscription.id
                                }
                            },
                            onVisible = onVisible,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingAuthorSection(
    subscription: AuthorSubscription,
    result: SearchItemResult?,
    expanded: Boolean,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onToggleExpanded: () -> Unit,
    onVisible: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(subscription.id) {
        onVisible(subscription.id)
    }

    Column(modifier = modifier) {
        CollapsibleAuthorHeader(
            title = subscription.name,
            expanded = expanded,
            onClick = onToggleExpanded,
        )

        if (!expanded) return@Column

        when (result) {
            null,
            SearchItemResult.Loading,
            -> GlobalSearchLoadingResultItem()
            is SearchItemResult.Success -> GlobalSearchCardRow(
                titles = result.result,
                getManga = getManga,
                onClick = onClickManga,
                onLongClick = onLongClickManga,
                selection = emptyList(),
            )
            is SearchItemResult.Error -> GlobalSearchErrorResultItem(result.throwable.message)
        }
    }
}
