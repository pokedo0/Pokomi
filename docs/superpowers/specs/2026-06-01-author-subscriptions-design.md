# Author Subscriptions Design

## Context

Komikku should add a Komikku-only feature for following author or artist search keywords. This feature is different from the existing library update flow: library updates check new chapters for known manga, while author subscriptions periodically search a selected source for new or related works matching an author keyword.

The feature should feel like a saved Browse search with a dedicated bottom navigation destination. It does not need source-specific author or artist field parsing. The subscription keyword is searched exactly like a normal Browse/global search query.

Reference UI: `stitch_mihon_ui_design_mockups/screen.png`.

## Goals

- Add a new bottom navigation tab named `Following` / `订阅`.
- Let users subscribe to the current global search keyword from the global search results page.
- Bind each subscribed keyword to exactly one source.
- Show each subscribed keyword as one author section in the Following tab.
- Load works by calling the bound source with the saved keyword as a normal search query.
- Keep the UI close to the provided mockup: author name header, refresh button, arrow button, and a horizontal manga row.

## Non-Goals

- Do not detect whether a source supports author or artist filters.
- Do not parse manga metadata to infer authors automatically.
- Do not support multiple sources for the same keyword in the MVP.
- Do not show the bound source name in the Following list.
- Do not persist full search result snapshots in the MVP.
- Do not implement background notifications for new author works in the first version.

## Confirmed Product Decisions

- A subscription is a saved Browse keyword plus one bound source.
- The same normalized keyword can only have one subscription.
- Changing the source for an existing keyword updates that subscription instead of creating another section.
- The Following tab displays only the author keyword as the section title. The bound source is internal state.
- The global search result page is the primary entry point for subscribing.

## User Experience

### Global Search Entry

When the user enters a keyword in global search, each source result header gets an additional heart action on the right side.

- Empty heart means the current keyword is not bound to that source.
- Filled heart means the current keyword is currently subscribed and bound to that source.
- Tapping an empty heart creates a subscription if the keyword is not subscribed.
- Tapping an empty heart for another source switches the existing keyword subscription to that source.
- Tapping the filled heart opens a confirmation dialog before removing the subscription.
- If the search keyword is blank, the heart action is hidden or disabled.

This makes source selection direct: the user subscribes by tapping the heart on the desired source row.

### Following Tab

The Following tab uses the mockup structure:

- Top app bar title: `Following` / `订阅`.
- Top actions follow the mockup: local subscription search, sort/filter, and overflow menu.
- Each subscription renders as one vertical section.
- Section title is the saved keyword/display name only.
- Section actions:
  - refresh: reload this keyword from its bound source.
  - arrow: open Browse global search with the keyword already filled.
- Manga results render in a horizontal row using the existing Browse/global search card style.
- Tapping a manga opens `MangaScreen`.

No source name appears in the visible section header.

### Empty and Error States

- No subscriptions: show an empty screen that directs users to Browse global search.
- Source missing or disabled: show the author section with a compact error state and allow source switching from global search.
- Search failure: show the error only inside that author section; other sections continue loading.
- No results: show the same no-results style used by Browse result rows.

## Data Model

The model should stay close to `saved_search`: a source-bound name/query pair, specialized for the Following tab.

Recommended table:

```sql
CREATE TABLE author_subscription(
    _id INTEGER NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    name TEXT NOT NULL,
    query TEXT NOT NULL,
    normalized_query TEXT NOT NULL,
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL,
    last_refresh_at INTEGER AS Long,
    sort_order INTEGER NOT NULL
);

CREATE UNIQUE INDEX author_subscription_normalized_query_index
ON author_subscription(normalized_query);
```

Field meanings:

- `source`: the source currently bound to this keyword.
- `name`: the visible title, defaulting to the original keyword.
- `query`: the exact Browse search keyword to send to the source.
- `normalized_query`: lowercase/trimmed/collapsed form used to enforce one subscription per keyword.
- `last_refresh_at`: updated after a successful manual or screen-triggered refresh.
- `sort_order`: supports stable user ordering later; default insertion order is enough for MVP.

The MVP does not need `filters_json`, because the requirement is ordinary keyword search. If future work adds saved source filters, this table can add `filters_json` using the same shape as `saved_search`.

## Domain and Data Boundaries

Add a small author subscription domain surface:

- `AuthorSubscription` model.
- Repository interface in domain.
- SQLDelight-backed repository in data.
- Interactors:
  - get subscriptions as a flow.
  - upsert subscription by keyword and source.
  - delete subscription by normalized keyword or id.
  - update last refresh metadata.

Register the repository and interactors through existing Injekt modules. Because this is Komikku-only behavior, new code should be marked with `// KMK -->` / `// KMK <--` where it touches shared Mihon/SY files.

## Search Execution

Following search should reuse the same behavior as Browse/global search:

- Resolve the bound `source` as a `CatalogueSource`.
- Call `source.getSearchManga(1, query.sanitize(), source.getFilterList())`.
- Convert network manga to domain manga with `toDomainManga(source.id)`.
- Deduplicate by URL.
- Persist/resolve local manga with `NetworkToLocalManga`.

The result is kept in screen state. It is not saved as subscription data.

## Loading and Rate Limiting

The Following screen should avoid firing every subscription at once.

Recommended MVP behavior:

- On first open, load the first 5 subscriptions.
- Limit concurrent source requests to 5, matching the existing global search worker-pool size.
- Load additional sections when they become visible or near-visible.
- Refresh button reloads only that author section.
- Pull-to-refresh, if added, refreshes only currently loaded sections.

This keeps startup responsive and reduces pressure on extensions.

## Navigation

Bottom navigation:

- Add `FollowingTab` between `LibraryTab` and `UpdatesTab`.
- Update tab indexes so ordering remains stable.
- Preserve tablet navigation rail behavior.

Global search:

- Extend the source result header actions to include the subscription heart.
- The heart state is derived from the current search keyword and the existing subscription flow.

Following arrow action:

- Opens Browse global search with the saved keyword prefilled.
- This mirrors the user's original mental model: Following is a saved way back into Browse keyword search.

## UI Components

Reuse existing components where possible:

- `GlobalSearchCardRow` or its lower-level manga item layout for horizontal manga rows.
- Existing loading, error, and empty result row patterns from Browse.
- Material3 icon buttons for heart, refresh, and arrow actions.

If `GlobalSearchResultItem` becomes too source-specific, extract a small shared row-header component instead of duplicating the full Browse list implementation.

## Internationalization

This is Komikku-only UI, so all new strings must use `KMR` from `tachiyomi.i18n.kmk.KMR`.

Strings must be added only to:

`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

No non-base locale files should be edited.

Likely strings:

- Following / 订阅
- Subscribe author
- Unsubscribe author
- Switch subscribed source
- No followed authors
- Refresh author
- Open author search

## Backup and Restore

The MVP does not include backup/restore for author subscriptions. This keeps the first implementation focused on the Browse-to-Following workflow.

Future backup support should store only subscription metadata: query/name/source/sort order. It should not back up cached results.

## Testing and Verification Plan

Unit-level checks:

- Upsert creates a new subscription for a new normalized keyword.
- Upsert with the same normalized keyword changes the bound source instead of inserting a duplicate.
- Delete removes the active subscription.
- Query normalization handles surrounding spaces and repeated whitespace.

Manual behavior checks:

- Global search with a keyword shows heart actions on source rows.
- Tapping a source heart creates a subscription.
- Tapping a different source heart switches the subscription.
- Following tab shows one section for that keyword and does not display source name.
- Refresh reloads only that section.
- Arrow opens global search with the saved keyword.

Required repository verification after code implementation:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
```

## Rollout Notes

This should be implemented as a Komikku feature. Touch shared Mihon/SY surfaces only where necessary for navigation, global search actions, and shared row reuse. Keep new strings in `i18n-kmk` and avoid editing translated locale files.

The MVP creates the durable foundation: one keyword, one source, one Following section. Future versions can add source switching from the Following overflow menu, custom display names, sorting, background refresh, and notifications for newly discovered works.
