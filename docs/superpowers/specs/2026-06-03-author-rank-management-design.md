# Author Rank Management Design

## Context

Following can now show many subscribed authors as collapsible sections. With a long list, users may need to scroll for a long time to reach a specific author. The app needs a hidden management surface that exposes the full author list, supports manual ordering, and keeps Following in sync with the saved order.

Reference design: `stitch_design/author__rank_list`.

## Goals

- Add a compact sort/rank entry button in the Following top bar.
- Open an author management page from that button.
- Show a long, reorderable author list based on followed author subscriptions.
- Support drag reorder, pin/unpin, move to top, and move to bottom.
- Keep Following author order linked to the saved management order.
- When opening the management page, align the list to the author currently visible in Following.
- When returning after a real change, keep the user oriented with scroll alignment and a short highlight animation.
- If the user saves with no changes, close without triggering Following scroll, highlight, reload, or any visible reorder effect.

## Non-Goals

- Do not change Following manga result cards.
- Do not add extra management components beyond the reference list actions.
- Do not add search/filter inside the management page for this iteration.
- Do not make the management page manage source switching or subscription deletion.

## Entry Point

Following top bar gets a small sorting action on the right, next to existing app bar actions if any.

Recommended icon: Material `Sort` or `FormatLineSpacing`, with content description from `KMR`.

Clicking the action opens an `AuthorRankScreen` using Voyager navigation. It receives the currently visible author subscription id as an optional initial anchor.

## Management Page Layout

Use a full-screen management page instead of a bottom sheet. A bottom sheet is too cramped for a long list with drag handles and three row actions.

Top app bar:

- Back icon on the left.
- Title: `作者管理` / Author management.
- Save text action on the right.

List item:

- Height around 72dp, matching the reference.
- Left drag handle.
- Center author name, single line with ellipsis.
- Right actions:
  - Pin/unpin.
  - Move to top.
  - Move to bottom.
- Disabled state:
  - Move-to-top disabled for the first movable position.
  - Move-to-bottom disabled for the last movable position.
  - Pinned icon uses active tint when pinned.

The list should use existing Compose + Material 3 components and `sh.calvin.reorderable`, matching existing reorder screens in the project.

## Ordering Semantics

The existing `author_subscription.sort_order` remains the canonical persisted manual order.

Add a persisted pin flag:

- `pinned INTEGER NOT NULL DEFAULT 0`

Display order:

1. Pinned subscriptions first.
2. Then `sort_order ASC`.
3. Then `_id ASC`.

Pinning an author moves it into the pinned block while preserving a stable `sort_order`. Unpinning returns it to the unpinned block according to `sort_order`.

Move-to-top behavior:

- If pinned, move to the top of the pinned block.
- If not pinned, move to the top of the unpinned block.

Move-to-bottom behavior:

- If pinned, move to the bottom of the pinned block.
- If not pinned, move to the bottom of the unpinned block.

Drag behavior:

- Dragging within the same block reorders within that block.
- Dragging across pinned/unpinned boundary updates the item's pinned state to match the destination block.

## Data Flow

Repository additions:

- `updateOrder(items: List<AuthorSubscriptionOrderUpdate>)`
- `updatePinned(id: Long, pinned: Boolean)`

Interactor additions:

- `ReorderAuthorSubscriptions`
- `MoveAuthorSubscriptionToTop`
- `MoveAuthorSubscriptionToBottom`
- `ToggleAuthorSubscriptionPinned`

All order-changing operations should write a normalized sequence of `sort_order` values so the database remains deterministic.

Following already observes `GetAuthorSubscriptions.subscribeAll()`. Once the query orders by `pinned DESC, sort_order ASC, _id ASC`, Following receives the new order automatically.

## Change Detection

The management screen stores an initial snapshot when opened:

- subscription id
- pinned state
- list index/order

When Save is tapped:

- If the current snapshot matches the initial snapshot, close immediately.
- Do not call repository order writes.
- Do not tell Following to scroll.
- Do not trigger highlight or reorder animation on Following.

If the snapshot differs:

- Persist the order/pin changes.
- Return the anchor author id to Following.
- Following scrolls to that author in the new order and briefly highlights it.

Back navigation without Save should discard local changes and return without affecting Following.

## Scroll Position Link

Following owns a `LazyListState`.

Following derives the visible anchor author id from the first visible author header or nearest visible subscription section. This value is passed to `AuthorRankScreen` when opening.

On management screen entry:

- If the anchor id exists, the list starts at or animates to that author.
- The anchored row gets a short tonal highlight.
- If the author no longer exists, start at the top.

On return after changed Save:

- Following receives the saved anchor id.
- If the author still exists, Following `animateScrollToItem()` scrolls to that section.
- The author header gets a short tonal highlight.
- If the author no longer exists, no scroll is attempted.

## Animation

Follow Material 3 motion principles:

- Open/close management page: shared-axis or fade-through transition through Voyager where possible.
- Reorder drag: elevated row, tonal container background, smooth item placement animation.
- Move top/bottom and pin/unpin: list item movement uses `animateItem()`.
- Anchor highlight: 600-900ms tonal container fade, then return to normal surface.
- No-change Save: simple close transition only, no Following-side motion.

## Error Handling

- If persistence fails, keep the management screen open and show a snackbar.
- Disable Save while a write is in progress.
- If subscriptions change externally while the management screen is open, merge by id:
  - Keep local order for still-present items.
  - Append newly added authors to the end of the unpinned block.
  - Remove deleted authors from the local list.

## Testing

Unit tests:

- Ordering query/model sorts pinned before unpinned.
- Move top changes only the target block.
- Move bottom changes only the target block.
- Toggle pinned moves the item to the correct block.
- Save with no changes performs no repository write and reports no Following anchor action.
- Save with changes persists order and reports the anchor id.

Compile and verification:

- `./gradlew spotlessApply`
- `./gradlew spotlessCheck`
- `./gradlew assembleDebug`

