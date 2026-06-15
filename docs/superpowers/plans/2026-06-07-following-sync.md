# Following Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backup, restore, and cloud sync support for following authors while excluding following cache data.

**Architecture:** Add following as a separate backup section with a whole-list timestamp. Creation writes the current list and timestamp, restore applies the newer full list, and sync merge chooses the newer full list. Existing backup options gain a following checkbox while preserving old option-array compatibility.

**Tech Stack:** Kotlin, kotlinx serialization protobuf, SQLDelight, Injekt, JUnit 5, Gradle.

---

### Task 1: Add Tests For Option Compatibility And Following Merge

**Files:**
- Create: `app/src/test/java/eu/kanade/tachiyomi/data/backup/BackupOptionsCompatibilityTest.kt`
- Create: `app/src/test/java/eu/kanade/tachiyomi/data/sync/FollowingSyncMergeTest.kt`

- [ ] Write failing tests for old boolean-array decoding.
- [ ] Write failing tests for following whole-list latest-wins merge.
- [ ] Run focused tests and confirm RED.

### Task 2: Add Following Backup Models And Options

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupAuthorSubscription.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/RestoreOptions.kt`
- Modify: `app/src/main/java/eu/kanade/domain/sync/models/SyncSettings.kt`
- Modify: `app/src/main/java/eu/kanade/domain/sync/SyncPreferences.kt`

- [ ] Add following option at the end of option arrays.
- [ ] Make boolean-array decoding length-safe.
- [ ] Add following string label using existing Komikku base string.
- [ ] Run focused tests and confirm option compatibility GREEN.

### Task 3: Add Following Create And Restore Components

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/AuthorSubscriptionBackupCreator.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/AuthorSubscriptionRestorer.kt`
- Modify: `domain/src/main/java/tachiyomi/domain/authorSubscription/service/FollowingPreferences.kt`
- Modify: `domain/src/main/java/tachiyomi/domain/authorSubscription/repository/AuthorSubscriptionRepository.kt`
- Modify: `data/src/main/java/tachiyomi/data/authorSubscription/AuthorSubscriptionRepositoryImpl.kt`
- Modify: `data/src/main/sqldelight/tachiyomi/data/author_subscription.sq`

- [ ] Add following whole-list timestamp preference.
- [ ] Add repository operation for replacing following list from backup.
- [ ] Add creator that excludes cache data.
- [ ] Add restorer that applies only newer full lists.

### Task 4: Wire Backup, Restore, And Sync

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt`
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt`
- Modify: `app/src/main/java/eu/kanade/presentation/more/settings/screen/data/SyncSettingsSelector.kt`

- [ ] Include following in backup creation when selected.
- [ ] Restore following when selected.
- [ ] Include following in sync options and sync restore.
- [ ] Merge following by newer whole-list timestamp.
- [ ] Preserve feeds in sync merge.
- [ ] Run focused tests and confirm GREEN.

### Task 5: Verify

- [ ] Run `./gradlew spotlessApply`.
- [ ] Run `./gradlew spotlessCheck`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Review diff for accidental locale edits or cache sync.
