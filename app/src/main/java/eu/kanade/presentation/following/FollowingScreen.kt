package eu.kanade.presentation.following

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.library.components.CollapsibleAuthorHeader
import eu.kanade.tachiyomi.ui.following.AuthorRankOrderSnapshotItem
import eu.kanade.tachiyomi.ui.following.FollowingItemResult
import kotlinx.coroutines.delay
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun FollowingScreen(
    subscriptions: List<AuthorSubscription>,
    results: Map<Long, FollowingItemResult>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onPullRefresh: () -> Unit,
    onRefresh: (Long) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onRankAuthors: (Long?) -> Unit,
    onVisible: (Long) -> Unit,
    pendingRankAnchorId: Long?,
    pendingRankOrderSnapshot: List<AuthorRankOrderSnapshotItem>?,
    highlightedAuthorId: Long?,
    onRankAnchorShown: (Long) -> Unit,
    onHighlightConsumed: (Long) -> Unit,
) {
    val isRefreshing = results.values.any {
        it is FollowingItemResult.Loading ||
            it is FollowingItemResult.RateLimited ||
            (it is FollowingItemResult.Success && it.refreshing)
    }
    var collapsedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val collapsedIdSet = remember(collapsedIds) { collapsedIds.toSet() }
    val lazyListState = rememberLazyListState()
    val translateAuthorName = rememberAuthorNameTranslator()
    val currentVisibleAuthorId by remember(subscriptions) {
        derivedStateOf {
            subscriptions.getOrNull(lazyListState.firstVisibleItemIndex)?.id
        }
    }
    val currentOrderSnapshot = remember(subscriptions) {
        subscriptions.map {
            AuthorRankOrderSnapshotItem(
                id = it.id,
                sortOrder = it.sortOrder,
                pinned = it.pinned,
            )
        }
    }

    LaunchedEffect(pendingRankAnchorId, pendingRankOrderSnapshot, currentOrderSnapshot) {
        val anchorId = pendingRankAnchorId ?: return@LaunchedEffect
        if (pendingRankOrderSnapshot == null || pendingRankOrderSnapshot == currentOrderSnapshot) {
            return@LaunchedEffect
        }

        val index = subscriptions.indexOfFirst { it.id == anchorId }
        if (index >= 0) {
            lazyListState.animateScrollToItem(index)
            onRankAnchorShown(anchorId)
        }
    }

    LaunchedEffect(highlightedAuthorId) {
        val anchorId = highlightedAuthorId ?: return@LaunchedEffect
        delay(AUTHOR_HIGHLIGHT_DURATION_MS)
        onHighlightConsumed(anchorId)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.following),
                actions = {
                    IconButton(onClick = onRefreshAll) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(KMR.strings.following_refresh_all),
                        )
                    }
                    IconButton(onClick = { onRankAuthors(currentVisibleAuthorId) }) {
                        Icon(
                            imageVector = Icons.Outlined.SortByAlpha,
                            contentDescription = stringResource(KMR.strings.action_reorder_authors),
                        )
                    }
                },
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
                ScrollbarLazyColumn(
                    state = lazyListState,
                    contentPadding = paddingValues + topSmallPaddingValues,
                ) {
                    items(subscriptions, key = { it.id }) { subscription ->
                        val expanded = subscription.id !in collapsedIdSet
                        FollowingAuthorSection(
                            subscription = subscription,
                            displayName = translateAuthorName(subscription.name),
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
                            onRefresh = onRefresh,
                            onOpenSearch = onOpenSearch,
                            onVisible = onVisible,
                            highlighted = subscription.id == highlightedAuthorId,
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
    displayName: String,
    result: FollowingItemResult?,
    expanded: Boolean,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    onToggleExpanded: () -> Unit,
    onRefresh: (Long) -> Unit,
    onOpenSearch: (String) -> Unit,
    onVisible: (Long) -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(subscription.id) {
        onVisible(subscription.id)
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "followingAuthorHighlight",
    )

    Column(
        modifier = modifier.background(backgroundColor),
    ) {
        CollapsibleAuthorHeader(
            title = displayName,
            expanded = expanded,
            onClick = onToggleExpanded,
            titleTextStyle = MaterialTheme.typography.titleMedium,
            contentPadding = PaddingValues(
                start = MaterialTheme.padding.small,
                end = MaterialTheme.padding.small,
                top = 4.dp,
                bottom = 0.dp,
            ),
        ) {
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

        if (!expanded) return@Column

        when (result) {
            null,
            FollowingItemResult.Loading,
            -> GlobalSearchLoadingResultItem()
            is FollowingItemResult.RateLimited -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GlobalSearchLoadingResultItem()
                Text(
                    text = stringResource(
                        KMR.strings.following_rate_limited,
                        result.attempt,
                        result.max,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            FollowingItemResult.Stalled -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(KMR.strings.following_stalled),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { onRefresh(subscription.id) }) {
                    Text(text = stringResource(KMR.strings.following_retry))
                }
            }
            is FollowingItemResult.Success -> GlobalSearchCardRow(
                titles = result.result,
                getManga = getManga,
                onClick = onClickManga,
                onLongClick = onLongClickManga,
                selection = emptyList(),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.padding.small,
                    vertical = 0.dp,
                ),
            )
            is FollowingItemResult.Error -> GlobalSearchErrorResultItem(result.throwable.message)
        }
    }
}

private const val AUTHOR_HIGHLIGHT_DURATION_MS = 2_000L
