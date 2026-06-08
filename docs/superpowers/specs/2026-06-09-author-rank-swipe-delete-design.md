# Author Rank Swipe Delete Design

## Goal

Add left-swipe deletion to the Author management list. The interaction should feel like a current Android/Material dismiss action and should fit the existing edit-then-save workflow.

## Interaction

Use the reveal-background pattern. Swiping a row from right to left moves the row content with the finger and reveals an error-colored background with a delete icon aligned to the trailing edge. If the swipe passes the dismiss threshold, the row is removed from the editable list. If it does not pass the threshold, the row settles back into place.

Only end-to-start swipes delete. Start-to-end swipes are ignored.

## Data Behavior

Deleting in this screen is staged. A dismissed author disappears from the current Author management list and marks the screen as changed, but the repository is not modified until the user taps Save.

Saving deletes authors that were present in the initial snapshot but are absent from the current list, then saves the remaining order and pinned state. If saving fails at either step, the screen stays open and shows the existing save-failed snackbar.

## UI Integration

The implementation should reuse Material3 `SwipeToDismissBox` so threshold, settling animation, and gesture behavior follow platform conventions. The row content and existing drag handle, pin, move-to-top, and move-to-bottom actions remain unchanged.

When the screen is saving, swipe deletion is disabled along with the existing row actions.

## Tests

Add model-level tests for staged deletion and save-time persistence. UI gesture animation is verified by compile/build because the project does not have Compose UI tests for this screen.
