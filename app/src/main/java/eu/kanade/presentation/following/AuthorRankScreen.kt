package eu.kanade.presentation.following

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.following.AuthorRankScreenModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

@Composable
fun AuthorRankScreen(
    state: AuthorRankScreenModel.State,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onTogglePinned: (Long) -> Unit,
    onMoveToTop: (Long) -> Unit,
    onMoveToBottom: (Long) -> Unit,
    onDismissError: () -> Unit,
    onSave: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(KMR.strings.author_rank_save_failed)

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onDismissError()
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(KMR.strings.author_management),
                navigateUp = navigateUp,
                actions = {
                    TextButton(
                        enabled = !state.saving,
                        onClick = onSave,
                    ) {
                        Text(text = stringResource(KMR.strings.action_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        AuthorRankContent(
            state = state,
            paddingValues = paddingValues,
            onReorder = onReorder,
            onTogglePinned = onTogglePinned,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
        )
    }
}

@Composable
private fun AuthorRankContent(
    state: AuthorRankScreenModel.State,
    paddingValues: PaddingValues,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onTogglePinned: (Long) -> Unit,
    onMoveToTop: (Long) -> Unit,
    onMoveToBottom: (Long) -> Unit,
) {
    val initialIndex = state.items.indexOfFirst { it.id == state.initialAuthorId }.coerceAtLeast(0)
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        if (!state.saving) {
            onReorder(from.index, to.index)
        }
    }
    var initialScrollHandled by remember { mutableStateOf(false) }

    LaunchedEffect(state.items, state.initialAuthorId) {
        val index = state.items.indexOfFirst { it.id == state.initialAuthorId }
        if (!initialScrollHandled && index >= 0) {
            lazyListState.scrollToItem(index)
            initialScrollHandled = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues +
            topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = state.items,
            key = { it.id },
        ) { subscription ->
            ReorderableItem(reorderableState, subscription.id) {
                AuthorRankRow(
                    subscription = subscription,
                    highlighted = subscription.id == state.highlightedAuthorId,
                    enabled = !state.saving,
                    onTogglePinned = { onTogglePinned(subscription.id) },
                    onMoveToTop = { onMoveToTop(subscription.id) },
                    onMoveToBottom = { onMoveToBottom(subscription.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.AuthorRankRow(
    subscription: AuthorSubscription,
    highlighted: Boolean,
    enabled: Boolean,
    onTogglePinned: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "authorRankRowBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(backgroundColor)
            .padding(
                start = MaterialTheme.padding.small,
                end = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(KMR.strings.action_reorder_authors),
            modifier = Modifier
                .padding(MaterialTheme.padding.medium)
                .draggableHandle(enabled = enabled),
        )
        Text(
            text = subscription.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            enabled = enabled,
            onClick = onTogglePinned,
        ) {
            Icon(
                imageVector = if (subscription.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(
                    if (subscription.pinned) {
                        KMR.strings.action_unpin_author
                    } else {
                        KMR.strings.action_pin_author
                    },
                ),
            )
        }
        IconButton(
            enabled = enabled,
            onClick = onMoveToTop,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignTop,
                contentDescription = stringResource(KMR.strings.action_move_author_top),
            )
        }
        IconButton(
            enabled = enabled,
            onClick = onMoveToBottom,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignBottom,
                contentDescription = stringResource(KMR.strings.action_move_author_bottom),
            )
        }
    }
}
