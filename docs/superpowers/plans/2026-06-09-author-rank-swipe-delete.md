# Author Rank Swipe Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Material-style left-swipe deletion to the Author management list.

**Architecture:** Keep deletion staged in `AuthorRankScreenModel` so it matches existing sort/pin edits. The presentation layer uses Material3 `SwipeToDismissBox` to trigger the staged removal.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Voyager screen models, JUnit/Kotest, Gradle Spotless.

---

### Task 1: Model staged deletion

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreenModel.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreenModelTest.kt`

- [ ] Write a failing test that creates a model with three authors, calls `removeAuthor(2)`, and expects author 2 to disappear while remaining authors have normalized order.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.ui.following.AuthorRankScreenModelTest` and confirm the test fails because `removeAuthor` is missing.
- [ ] Add `removeAuthor(id: Long)` to `AuthorRankScreenModel`. It should ignore requests while saving, remove the author by id, normalize remaining authors, and clear `error`.
- [ ] Re-run the same test and confirm it passes.

### Task 2: Persist staged deletions on Save

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreenModel.kt`
- Test: `app/src/test/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreenModelTest.kt`

- [ ] Write a failing test that removes author 2, calls `save`, and expects the delete interactor to receive id 2 before the remaining order is saved.
- [ ] Run the same focused test command and confirm it fails because deleted authors are not persisted.
- [ ] Inject `DeleteAuthorSubscription` into `AuthorRankScreenModel` and call it for ids that exist in `initialSnapshot` but not in current `items`, then call `ReorderAuthorSubscriptions` for the remaining items.
- [ ] Re-run the focused test and confirm it passes.

### Task 3: Add SwipeToDismiss UI

**Files:**
- Modify: `app/src/main/java/eu/kanade/presentation/following/AuthorRankScreen.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/following/AuthorRankScreen.kt`

- [ ] Add an `onRemoveAuthor` callback from the Voyager screen to the presentation screen and row.
- [ ] Wrap each author row in `SwipeToDismissBox`.
- [ ] Use end-to-start only, show an error-container background with a trailing delete icon, and call `onRemoveAuthor(subscription.id)` only when the dismiss value becomes `EndToStart`.
- [ ] Keep the row disabled while saving.

### Task 4: Verify

**Files:**
- Existing Kotlin files only.

- [ ] Run `./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.ui.following.AuthorRankScreenModelTest`.
- [ ] Run `./gradlew spotlessApply`.
- [ ] Run `./gradlew spotlessCheck`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Inspect `git diff` and confirm no non-base locale files were changed.
