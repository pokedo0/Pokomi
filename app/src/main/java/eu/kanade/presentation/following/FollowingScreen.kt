package eu.kanade.presentation.following

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun FollowingScreen(
    subscriptions: List<AuthorSubscription>,
    results: Map<Long, SearchItemResult>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
    onVisible: (Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.following),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (subscriptions.isEmpty()) {
            EmptyScreen(
                stringRes = KMR.strings.following_empty,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            LazyColumn(contentPadding = paddingValues) {
                items(subscriptions, key = { it.id }) { subscription ->
                    FollowingAuthorSection(
                        subscription = subscription,
                        result = results[subscription.id],
                        getManga = getManga,
                        onClickManga = onClickManga,
                        onLongClickManga = onLongClickManga,
                        onRefresh = onRefresh,
                        onOpenSearch = onOpenSearch,
                        onVisible = onVisible,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowingAuthorSection(
    subscription: AuthorSubscription,
    result: SearchItemResult?,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
    onVisible: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(subscription.id) {
        onVisible(subscription.id)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.extraSmall,
                    top = MaterialTheme.padding.medium,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subscription.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Row {
                IconButton(onClick = { onRefresh(subscription.id) }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(KMR.strings.following_refresh_author),
                    )
                }
                IconButton(onClick = { onOpenSearch(subscription.query) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(KMR.strings.following_open_author_search),
                    )
                }
            }
        }

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
