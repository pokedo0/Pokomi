package eu.kanade.presentation.library.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.following.rememberAuthorNameTranslator
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun LibraryAuthorSections(
    categories: List<Category>,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    if (categories.all { getItemsForCategory(it).isEmpty() }) {
        LibraryPagerEmptyScreen(
            searchQuery = searchQuery,
            hasActiveFilters = hasActiveFilters,
            contentPadding = contentPadding,
            onGlobalSearchClicked = onGlobalSearchClicked,
        )
        return
    }

    var collapsedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val collapsedIdSet = remember(collapsedIds) { collapsedIds.toSet() }
    val toggleCollapsed: (Category) -> Unit = { category ->
        collapsedIds = if (category.id in collapsedIdSet) {
            collapsedIds - category.id
        } else {
            collapsedIds + category.id
        }
    }

    val displayMode by getDisplayMode(0)
    val columns by if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        remember(isLandscape) { getColumnsForOrientation(isLandscape) }
    } else {
        remember { mutableIntStateOf(0) }
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            LibraryAuthorListSections(
                categories = categories,
                collapsedIds = collapsedIdSet,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getItemsForCategory = getItemsForCategory,
                onToggleCollapsed = toggleCollapsed,
                onClickManga = onClickManga,
                onLongClickManga = onLongClickManga,
                onClickContinueReading = onClickContinueReading,
            )
        }
        LibraryDisplayMode.CompactGrid,
        LibraryDisplayMode.CoverOnlyGrid,
        LibraryDisplayMode.ComfortableGrid,
        LibraryDisplayMode.ComfortableGridPanorama,
        -> {
            LibraryAuthorGridSections(
                categories = categories,
                collapsedIds = collapsedIdSet,
                displayMode = displayMode,
                columns = columns,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getItemsForCategory = getItemsForCategory,
                onToggleCollapsed = toggleCollapsed,
                onClickManga = onClickManga,
                onLongClickManga = onLongClickManga,
                onClickContinueReading = onClickContinueReading,
            )
        }
    }
}

@Composable
private fun LibraryAuthorListSections(
    categories: List<Category>,
    collapsedIds: Set<Long>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onToggleCollapsed: (Category) -> Unit,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    val translateAuthorName = rememberAuthorNameTranslator()
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        categories.forEach { category ->
            val expanded = category.id !in collapsedIds
            item(
                key = "author-header-${category.id}",
                contentType = "library_author_header",
            ) {
                CollapsibleAuthorHeader(
                    title = translateAuthorName(category.name),
                    expanded = expanded,
                    onClick = { onToggleCollapsed(category) },
                    modifier = Modifier.animateItem(),
                )
            }

            if (expanded) {
                items(
                    items = getItemsForCategory(category),
                    key = { "${category.id}-${it.id}" },
                    contentType = { "library_author_list_item" },
                ) { libraryItem ->
                    val manga = libraryItem.libraryManga.manga
                    MangaListItem(
                        isSelected = manga.id in selection,
                        title = manga.title,
                        coverData = MangaCover(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            ogUrl = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        badge = {
                            DownloadsBadge(count = libraryItem.downloadCount)
                            UnreadBadge(count = libraryItem.unreadCount)
                            LanguageBadge(
                                isLocal = libraryItem.isLocal,
                                sourceLanguage = libraryItem.sourceLanguage,
                                useLangIcon = libraryItem.useLangIcon,
                            )
                            SourceIconBadge(source = libraryItem.source)
                        },
                        onLongClick = { onLongClickManga(category, libraryItem.libraryManga) },
                        onClick = { onClickManga(category, libraryItem.libraryManga) },
                        onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                            { onClickContinueReading(libraryItem.libraryManga) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryAuthorGridSections(
    categories: List<Category>,
    collapsedIds: Set<Long>,
    displayMode: LibraryDisplayMode,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    getItemsForCategory: (Category) -> List<LibraryItem>,
    onToggleCollapsed: (Category) -> Unit,
    onClickManga: (Category, LibraryManga) -> Unit,
    onLongClickManga: (Category, LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    val translateAuthorName = rememberAuthorNameTranslator()
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        categories.forEach { category ->
            val expanded = category.id !in collapsedIds
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "author-header-${category.id}",
                contentType = "library_author_header",
            ) {
                CollapsibleAuthorHeader(
                    title = translateAuthorName(category.name),
                    expanded = expanded,
                    onClick = { onToggleCollapsed(category) },
                    modifier = Modifier.animateItem(),
                )
            }

            if (expanded) {
                items(
                    items = getItemsForCategory(category),
                    key = { "${category.id}-${it.id}" },
                    contentType = { "library_author_grid_item" },
                ) { libraryItem ->
                    val manga = libraryItem.libraryManga.manga
                    val coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        ogUrl = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    )
                    val onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                        { onClickContinueReading(libraryItem.libraryManga) }
                    } else {
                        null
                    }

                    when (displayMode) {
                        LibraryDisplayMode.CompactGrid,
                        LibraryDisplayMode.CoverOnlyGrid,
                        -> {
                            MangaCompactGridItem(
                                isSelected = manga.id in selection,
                                title = manga.title.takeIf { displayMode is LibraryDisplayMode.CompactGrid },
                                coverData = coverData,
                                coverBadgeStart = {
                                    DownloadsBadge(count = libraryItem.downloadCount)
                                    UnreadBadge(count = libraryItem.unreadCount)
                                },
                                coverBadgeEnd = {
                                    LanguageBadge(
                                        isLocal = libraryItem.isLocal,
                                        sourceLanguage = libraryItem.sourceLanguage,
                                        useLangIcon = libraryItem.useLangIcon,
                                    )
                                    SourceIconBadge(source = libraryItem.source)
                                },
                                onLongClick = { onLongClickManga(category, libraryItem.libraryManga) },
                                onClick = { onClickManga(category, libraryItem.libraryManga) },
                                onClickContinueReading = onClickContinueReading,
                            )
                        }
                        LibraryDisplayMode.ComfortableGrid,
                        LibraryDisplayMode.ComfortableGridPanorama,
                        -> {
                            MangaComfortableGridItem(
                                isSelected = manga.id in selection,
                                title = manga.title,
                                coverData = coverData,
                                coverBadgeStart = {
                                    DownloadsBadge(count = libraryItem.downloadCount)
                                    UnreadBadge(count = libraryItem.unreadCount)
                                },
                                coverBadgeEnd = {
                                    LanguageBadge(
                                        isLocal = libraryItem.isLocal,
                                        sourceLanguage = libraryItem.sourceLanguage,
                                        useLangIcon = libraryItem.useLangIcon,
                                    )
                                    SourceIconBadge(source = libraryItem.source)
                                },
                                onLongClick = { onLongClickManga(category, libraryItem.libraryManga) },
                                onClick = { onClickManga(category, libraryItem.libraryManga) },
                                onClickContinueReading = onClickContinueReading,
                                usePanoramaCover = displayMode is LibraryDisplayMode.ComfortableGridPanorama,
                            )
                        }
                        LibraryDisplayMode.List -> Unit
                    }
                }
            }
        }
    }
}
