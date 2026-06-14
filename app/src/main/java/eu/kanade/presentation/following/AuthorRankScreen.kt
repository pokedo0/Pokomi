package eu.kanade.presentation.following

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.following.AuthorRankScreenModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AuthorRankScreen(
    state: AuthorRankScreenModel.State,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onTogglePinned: (Long) -> Unit,
    onMoveToTop: (Long) -> Unit,
    onMoveToBottom: (Long) -> Unit,
    onRemoveAuthor: (Long) -> Unit,
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
            onRemoveAuthor = onRemoveAuthor,
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
    onRemoveAuthor: (Long) -> Unit,
) {
    val initialIndex = state.items.indexOfFirst { it.id == state.initialAuthorId }.coerceAtLeast(0)
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val translateAuthorName = rememberAuthorNameTranslator()
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

    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues,
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> item.id },
        ) { index, subscription ->
            ReorderableItem(reorderableState, subscription.id) { isDragging ->
                Column(modifier = Modifier.animateItem()) {
                    SwipeToDeleteAuthor(
                        enabled = !state.saving,
                        onRemove = { onRemoveAuthor(subscription.id) },
                    ) {
                        AuthorRankRow(
                            subscription = subscription,
                            displayName = translateAuthorName(subscription.name),
                            enabled = !state.saving,
                            onTogglePinned = { onTogglePinned(subscription.id) },
                            onMoveToTop = { onMoveToTop(subscription.id) },
                            onMoveToBottom = { onMoveToBottom(subscription.id) },
                        )
                    }
                    if (!isDragging && index < state.items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteAuthor(
    enabled: Boolean,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == EndToStart) {
                onRemove()
            }
            it == EndToStart
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { AuthorDeleteBackground(dismissState) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
    ) {
        content()
    }
}

@Composable
private fun AuthorDeleteBackground(dismissState: SwipeToDismissBoxState) {
    val direction = dismissState.dismissDirection
    val targetState = dismissState.targetValue
    val backgroundColor by animateColorAsState(
        when (direction) {
            EndToStart ->
                MaterialTheme.colorScheme.errorContainer
                    .copy(alpha = if (targetState == Settled) 0.45f else 1f)
            Settled,
            StartToEnd,
            -> MaterialTheme.colorScheme.surface
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (direction == EndToStart) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.AuthorRankRow(
    subscription: AuthorSubscription,
    displayName: String,
    enabled: Boolean,
    onTogglePinned: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = {})
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = stringResource(KMR.strings.action_reorder_authors),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(end = MaterialTheme.padding.medium)
                .draggableHandle(enabled = enabled),
        )
        Text(
            text = displayName,
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
