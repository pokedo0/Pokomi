package eu.kanade.presentation.following

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.following.AuthorRankScreenModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.authorSubscription.model.AuthorSubscription
import tachiyomi.i18n.MR
import tachiyomi.i18n.pkm.PKMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

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
    val errorMessage = stringResource(PKMR.strings.author_rank_save_failed)

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onDismissError()
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(PKMR.strings.author_management),
                navigateUp = navigateUp,
                actions = {
                    TextButton(
                        enabled = !state.saving,
                        onClick = onSave,
                    ) {
                        Text(text = stringResource(PKMR.strings.action_save))
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
    val context = LocalContext.current
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
                val displayName = translateAuthorName(subscription.name)
                val removedMessage = stringResource(PKMR.strings.author_removed, displayName)
                val removedToastMessage = remember(removedMessage, displayName) {
                    removedMessage.boldSubstring(displayName)
                }
                Column(modifier = Modifier.animateItem()) {
                    SwipeToDeleteAuthor(
                        enabled = !state.saving,
                        onRemove = {
                            onRemoveAuthor(subscription.id)
                            Toast.makeText(context.applicationContext, removedToastMessage, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        AuthorRankRow(
                            subscription = subscription,
                            displayName = displayName,
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
    val density = LocalDensity.current
    val view = LocalView.current
    var offsetX by remember { mutableStateOf(0f) }
    var animatingBack by remember { mutableStateOf(false) }
    var thresholdFeedbackSent by remember { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (animatingBack) 0f else offsetX,
        animationSpec = if (animatingBack) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            snap()
        },
        label = "authorDeleteOffset",
        finishedListener = {
            if (animatingBack) {
                offsetX = 0f
                animatingBack = false
                thresholdFeedbackSent = false
            }
        },
    )

    LaunchedEffect(enabled) {
        if (!enabled) {
            offsetX = 0f
            animatingBack = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AUTHOR_RANK_ROW_MIN_HEIGHT),
    ) {
        val deleteThresholdPx = with(density) {
            (maxWidth * AUTHOR_DELETE_SWIPE_THRESHOLD_FRACTION)
                .coerceIn(AUTHOR_DELETE_SWIPE_MIN_THRESHOLD, AUTHOR_DELETE_SWIPE_MAX_THRESHOLD)
                .toPx()
        }
        val maxOffsetPx = (deleteThresholdPx * AUTHOR_DELETE_SWIPE_MAX_OFFSET_MULTIPLIER)
            .coerceAtMost(with(density) { maxWidth.toPx() })

        AuthorDeleteBackground(
            revealed = animatedOffsetX < 0f,
            readyToDelete = animatedOffsetX <= -deleteThresholdPx,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(enabled, deleteThresholdPx, maxOffsetPx) {
                    if (!enabled) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = {
                            offsetX = animatedOffsetX
                            animatingBack = false
                            thresholdFeedbackSent = false
                        },
                        onDragEnd = {
                            if (offsetX <= -deleteThresholdPx) {
                                onRemove()
                            } else {
                                animatingBack = true
                            }
                        },
                        onDragCancel = {
                            animatingBack = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || offsetX < 0f) {
                                change.consume()
                                animatingBack = false
                                offsetX = (offsetX + dragAmount).coerceIn(-maxOffsetPx, 0f)
                                val readyToDelete = offsetX <= -deleteThresholdPx
                                if (readyToDelete && !thresholdFeedbackSent) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    thresholdFeedbackSent = true
                                } else if (!readyToDelete) {
                                    thresholdFeedbackSent = false
                                }
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.AuthorDeleteBackground(
    revealed: Boolean,
    readyToDelete: Boolean,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (revealed) {
            MaterialTheme.colorScheme.errorContainer
                .copy(alpha = if (readyToDelete) 1f else 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "authorDeleteBackground",
    )

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (revealed) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private fun String.boldSubstring(substring: String): SpannableString {
    val spannable = SpannableString(this)
    val start = indexOf(substring).takeIf { it >= 0 } ?: return spannable
    spannable.setSpan(
        StyleSpan(Typeface.BOLD),
        start,
        start + substring.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    return spannable
}

private const val AUTHOR_DELETE_SWIPE_THRESHOLD_FRACTION = 0.42f
private const val AUTHOR_DELETE_SWIPE_MAX_OFFSET_MULTIPLIER = 1.12f
private val AUTHOR_DELETE_SWIPE_MIN_THRESHOLD = 144.dp
private val AUTHOR_DELETE_SWIPE_MAX_THRESHOLD = 280.dp
private val AUTHOR_RANK_ROW_MIN_HEIGHT = 72.dp

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
            .heightIn(min = AUTHOR_RANK_ROW_MIN_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = {})
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = stringResource(PKMR.strings.action_reorder_authors),
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
                        PKMR.strings.action_unpin_author
                    } else {
                        PKMR.strings.action_pin_author
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
                contentDescription = stringResource(PKMR.strings.action_move_author_top),
            )
        }
        IconButton(
            enabled = enabled,
            onClick = onMoveToBottom,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignBottom,
                contentDescription = stringResource(PKMR.strings.action_move_author_bottom),
            )
        }
    }
}
