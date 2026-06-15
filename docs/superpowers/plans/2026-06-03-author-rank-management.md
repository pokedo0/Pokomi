# Author Rank Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full-screen author management page for followed authors with pinning, manual order, move-to-top, move-to-bottom, and synchronized Following order/scroll behavior.
**Architecture:** Persist author ordering on `author_subscription` with `sort_order` plus a new `pinned` flag. Expose repository and domain operations for batch order writes. Add a Voyager `AuthorRankScreen` opened from Following's top-right action. Following and the management page consume the same subscription order, while the management save callback scrolls/highlights Following only when a real change was saved.
**Tech Stack:** Kotlin, SQLDelight, Jetpack Compose Material3, Voyager, Injekt, sh.calvin.reorderable, moko-resources `KMR`.

---

## File Structure

Create:

- `data/src/main/sqldelight/tachiyomi/migrations/47.sqm`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/model/AuthorSubscriptionOrderUpdate.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/ReorderAuthorSubscriptions.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/MoveAuthorSubscriptionToTop.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/MoveAuthorSubscriptionToBottom.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/interactor/ToggleAuthorSubscriptionPinned.kt`
- `domain/src/test/java/tachiyomi/domain/authorSubscription/interactor/AuthorSubscriptionOrderingTest.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreenModel.kt`
- `app/src/main/java/eu/kanade/presentation/following/AuthorRankScreen.kt`

Modify:

- `data/src/main/sqldelight/tachiyomi/data/author_subscription.sq`
- `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionMapper.kt`
- `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionRepositoryImpl.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/model/AuthorSubscription.kt`
- `domain/src/main/java/tachiyomi/domain/authorSubscription/repository/AuthorSubscriptionRepository.kt`
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`
- `app/src/main/java/eu/kanade/presentation/following/FollowingScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/following/FollowingTab.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

## Tasks

- [ ] Task 1: Add persisted pin/order support

  Add `pinned INTEGER NOT NULL DEFAULT 0` to `author_subscription` and create migration `47.sqm`:

  ```sql
  ALTER TABLE author_subscription ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0;
  ```

  Update `selectAll` ordering:

  ```sql
  ORDER BY pinned DESC, sort_order ASC, _id ASC;
  ```

  Add SQLDelight mutations:

  ```sql
  updateOrder:
  UPDATE author_subscription
  SET sort_order = :sortOrder,
      pinned = :pinned,
      updated_at = :updatedAt
  WHERE _id = :id;

  updatePinned:
  UPDATE author_subscription
  SET pinned = :pinned,
      updated_at = :updatedAt
  WHERE _id = :id;
  ```

  Update the generated model mapping, domain `AuthorSubscription`, repository contract, and repository implementation. Batch order updates must run inside a DB transaction and use one `updatedAt` timestamp per save.

  Run after SQL edits:

  ```bash
  ./gradlew :data:generateSqlDelightInterface
  ```

- [ ] Task 2: Add domain ordering operations with tests

  Add:

  ```kotlin
  data class AuthorSubscriptionOrderUpdate(
      val id: Long,
      val sortOrder: Long,
      val pinned: Boolean,
  )
  ```

  Add pure ordering helpers in the reorder interactor file so tests can verify behavior without a database:

  - `normalize(items)` assigns stable `sortOrder` from the current visual order.
  - `reorder(items, fromIndex, toIndex)` reorders locally and normalizes.
  - `moveToTop(items, id)` moves inside pinned/unpinned visual order.
  - `moveToBottom(items, id)` moves inside pinned/unpinned visual order.
  - `togglePinned(items, id)` changes pinned state, then places pinned authors at the end of the pinned block and unpinned authors at the top of the unpinned block.

  Add interactors that call the helper and repository:

  - `ReorderAuthorSubscriptions.await(items: List<AuthorSubscription>)`
  - `MoveAuthorSubscriptionToTop.await(id: Long, items: List<AuthorSubscription>)`
  - `MoveAuthorSubscriptionToBottom.await(id: Long, items: List<AuthorSubscription>)`
  - `ToggleAuthorSubscriptionPinned.await(id: Long, items: List<AuthorSubscription>)`

  Register the interactors in `KMKDomainModule`.

  Test expectations:

  - Pinned authors sort before unpinned authors.
  - Drag reorder changes only visual order and normalized `sortOrder`.
  - Move-to-top and move-to-bottom keep the author in its pinned/unpinned block.
  - Toggle pin changes the block and produces a normalized order.

- [ ] Task 3: Add `AuthorRankScreenModel`

  State fields:

  ```kotlin
  data class State(
      val items: List<AuthorSubscription> = emptyList(),
      val initialSnapshot: List<AuthorRankSnapshotItem> = emptyList(),
      val initialAuthorId: Long? = null,
      val highlightedAuthorId: Long? = null,
      val saving: Boolean = false,
      val error: String? = null,
  ) {
      val hasChanges: Boolean
          get() = items.map { AuthorRankSnapshotItem(it.id, it.sortOrder, it.pinned) } != initialSnapshot
  }
  ```

  Behavior:

  - Load current subscriptions in repository order.
  - Keep all list changes local until Save.
  - Back discards local changes.
  - Save with no changes calls `navigateUp()` only.
  - Save with changes writes the full normalized list, invokes the result callback with the selected anchor author id, then closes.
  - On save failure, keep the page open and show a snackbar/error state.

- [ ] Task 4: Build full-screen author management UI

  Add a Voyager screen:

  ```kotlin
  class AuthorRankScreen(
      private val initialAuthorId: Long?,
      private val onSaved: (Long?) -> Unit,
  ) : Screen
  ```

  Presentation layout:

  - `Scaffold` with `AppBar`.
  - Title: `KMR.strings.author_management`.
  - Navigation: back arrow.
  - Action: Save text button, disabled while saving.
  - `LazyColumn` starts at the item matching `initialAuthorId`.
  - Each row is around 72dp high, matching the reference density.
  - Row content: drag handle, author name, pin action, move-to-top action, move-to-bottom action.
  - Use `rememberReorderableLazyListState` and `ReorderableItem`.
  - Use `Modifier.animateItem()` for list movement.
  - Use `animateColorAsState` for the opened anchor highlight and saved-return highlight.
  - Do not add extra right-side actions beyond pin/top/bottom.

  Required `KMR` strings in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:

  - `author_management`
  - `action_reorder_authors`
  - `action_save`
  - `action_pin_author`
  - `action_unpin_author`
  - `action_move_author_top`
  - `action_move_author_bottom`
  - `author_rank_save_failed`

- [ ] Task 5: Integrate with Following

  Update Following screen behavior:

  - Add a top-right sort/rank icon action.
  - Use `LazyListState` in the Following author section list.
  - Derive the current visible author id from `firstVisibleItemIndex` and the ordered subscription list.
  - On rank action click, push `AuthorRankScreen(initialAuthorId = currentVisibleAuthorId, onSaved = screenModel::onAuthorRankSaved)`.
  - `onAuthorRankSaved(anchorId)` stores a pending highlight/scroll target only when `anchorId != null`.
  - When subscriptions update after a changed save, scroll Following to the new index for `anchorId` and highlight that author section.
  - If the management page saved with no changes, it must not scroll, highlight, reload manually, or trigger a visible reorder animation beyond the normal page close.

  Keep the previously restored Following right-side section actions intact.

- [ ] Task 6: Format, verify, and commit

  Run required checks in order:

  ```bash
  ./gradlew spotlessApply
  ./gradlew spotlessCheck
  ./gradlew assembleDebug
  ```

  Self-check:

  ```bash
  git diff -- i18n-kmk/src i18n/src i18n-sy/src
  git branch --show-current
  git status --short
  ```

  Confirm no non-base locale strings were edited. Commit only the implementation files for this feature and leave unrelated dirty files alone.
