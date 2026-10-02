# Plan: Unjunkify Official-Docs Gapfix (SDD)

**Source spec**: `specs/001-unjunkify-device-cleaner/spec.md` (FR-001..FR-011, SC-002/SC-005/SC-006)
**Baseline**: branch `sdd/unjunkify-gapfix` @ `76de468` (T001–T032 done, review found 7 blockers vs Android official docs)
**Target**: make scan/delete/battery/score honest on API 29–36, offline-first, no INTERNET.

## Global Constraints (binding for every task + reviewer lens)

- Offline-first: no `INTERNET` permission added. Zero network calls in scan/clean/health. (`AndroidManifest.xml` must not contain `INTERNET`.)
- Suggest-and-confirm only: workers NEVER delete. Deletion only via explicit UI confirm → ViewModel → `JunkCleaner`. (`StorageScanWorker`, `BatteryCheckWorker` read-only.)
- Min permissions + graceful degrade: each permission justified in UI; denial skips that category, rest works. (spec FR-009)
- Scoped Storage compliance: `MediaStore` + `StorageStatsManager` only; no `MANAGE_EXTERNAL_STORAGE`, no `QUERY_ALL_PACKAGES`, no root. Sizes are estimates, labeled as such.
- KMP purity: `HealthScoreCalculator.healthScore()`, `ExemptFilter.filterExempt()` stay pure commonMain, unit-tested without Android APIs.
- Build gate: `./gradlew :androidApp:assembleDebug` passes. Kotlin 2.4.10, AGP 9.0.1, JDK 21, minSdk 29, targetSdk 36.
- No God files: scanner, cleaner, monitor, scorer, repos stay separate; ports keep `StorageScanner`/`JunkCleaner`/`BatteryMonitor` interfaces.

## Pre-flight conflict scan

| Pair / Task | Produces vs Consumes | Finding | Ruling |
|---|---|---|---|
| T1 vs T2 (both touch `AndroidManifest` + `MainActivity` permission flow) | T1 adds READ_MEDIA_* declarations + runtime request; T2 adds delete-consent `ActivityResult` launcher | Overlap in same files, sequential not parallel | Ruling: run T1 then T2 in order; T2 reuses T1's launcher pattern — cost if wrong: merge conflict, rework 30min |
| T3 vs T5 (StorageStatsManager) | T3 adds `StorageStatsProvider` aggregate; T5 consumes it for real `storageUsedPct` | Dependency T3→T5 | Ruling: T5 reads T3's provider interface from ledger; if T3 changes shape, T5 adapts — cost if wrong: compile break |
| T4 vs US-2 spec | Plan mandates optional `PACKAGE_USAGE_STATS`; manifest currently mis-declares it | Plan text vs code conflict | Ruling: spec FR-009 wins — remove hard manifest dependency, make Settings opt-in — cost if wrong: Play review friction |
| T8 vs all (CallBlocker leftovers) | `CallBlockerDatabase`, `AllowedContact`, `WhitelistScreen` still wired in `UnjunkifyApp` | Removing breaks DI if done early | Ruling: T8 last, after T1–T7 stable — cost if wrong: cascading DI breaks |
| Each task self-check | Tests specified vs code specified | All tasks name covering tests + files | Clean |

## Tasks

### Task 1 — Storage permissions (manifest + runtime + degrade)
**File**: `androidApp/src/main/AndroidManifest.xml`, `androidApp/.../MainActivity.kt`, `composeApp/.../ui/CleanScreen.kt` (inline rationale)
- Manifest: add `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (API 33+), `READ_EXTERNAL_STORAGE maxSdkVersion=32` (API 29–32). Keep `POST_NOTIFICATIONS`. Remove `PACKAGE_USAGE_STATS` hard declaration (moved to T4). No `MANAGE_EXTERNAL_STORAGE`.
- `MainActivity`: request media perms via `RequestMultiplePermissions`; on deny → scan still runs, affected categories skipped + inline explanation (FR-009).
- Tests: manifest grep test + manual deny-matrix (grant/deny → scan no crash).
- Verify: `./gradlew :androidApp:assembleDebug`, airplane-mode scan.

### Task 2 — Real MediaStore delete with user consent
**Files**: `composeApp/.../scanner/AndroidJunkCleaner.kt`, `composeApp/.../ui/CleanViewModel.kt`, `CleanScreen.kt`, `MainActivity.kt` (or dedicated `DeleteConsentHandler`)
- Replace bare `contentResolver.delete()` with: try delete → catch `RecoverableSecurityException` → collect URIs → `MediaStore.createDeleteRequest()` (API 30+) / `startIntentSenderForResult` fallback (API 29). Batch via `createDeleteRequest` per docs `Access media files > Remove an item`.
- `confirmDelete` only removes snapshots for URIs actually deleted; reclaimed = sum of actually-deleted sizes, not estimates.
- Tests: unit for reclaimed-accounting + instrumentation stub for consent path.
- Verify: delete own-created media succeeds silently; foreign media triggers system dialog; exempt never deleted.

### Task 3 — Honest storage aggregate (StorageStatsManager, no fake system-cache clean)
**Files**: `composeApp/.../scanner/AndroidStorageScanner.kt`, new `StorageStatsProvider` (androidMain) + interface (commonMain)
- Add `StorageStatsManager.queryStatsForPackage()` / `querySummary` aggregate for real used% (API 29+ `queryStatsForUser` with UUID). Scanner keeps MediaStore grouping (large/screenshot/installer/duplicates) + self-`cacheDir` only; remove implication of system-wide cache clean. Label sizes "estimate".
- Cap: newest 10k media, 30s budget, `take(150)` kept. Overheat pause out-of-scope, note in ledger.
- Tests: provider unit with fake + scanner cap test.
- Verify: Health score input now real (feeds T5).

### Task 4 — PACKAGE_USAGE_STATS opt-in fix
**Files**: `AndroidManifest.xml`, `UsageConsumerRanker.kt`, `HealthScreen.kt`
- Remove `<uses-permission PACKAGE_USAGE_STATS>` as install perm (keep `tools:ignore ProtectedPermissions` if declared, or drop). Add `Settings.ACTION_USAGE_ACCESS_SETTINGS` launcher + rationale card. Fix `hasUsagePermission()`: allow API 23+ via `AppOpsManager.unsafeCheckOpNoThrow`, not `SDK<Q → false`. Denial → battery/temp only, no crash.
- Rename UI copy from "background consumers" to "foreground time (estimate)" unless background heuristic added.
- Tests: permission-gated ranker test (granted/denied).
- Verify: deny → Health works with level/temp; grant → ranked list.

### Task 5 — Real healthScore inputs (no hardcoded 50)
**Files**: `CleanViewModel.kt:144`, `HealthScreen.kt:48-52`, `HealthScoreCalculator.kt` (unchanged formula), `BatteryRepository`
- `storageUsedPct` from T3 provider; `bgDrainHours` from 24h battery delta + foreground ranker (document heuristic); `overheat7d` from `BatteryRepository.history` count temp>=40/42. Remove `healthScore(50,0,0)`.
- Tests: `healthScore()` pure cases (existing formula) + ViewModel input-mapping test.
- Verify: score varies with pressure/drain/heat; cleanup records real score.

### Task 6 — Duplicate detection by content, not size
**File**: `AndroidStorageScanner.kt:findDuplicates`
- Replace `groupBy(size)` with: group by (size bucket) → sample hash (first 64KB + DISPLAY_NAME normalized) → only flag same-hash as DUPLICATE; different-files-same-size never flagged. Keep `take(20)`, per-item confirm preserved.
- Tests: same-size-different-content → 0 duplicates; same-content → flagged.
- Verify: SC-002 <30s retained.

### Task 7 — Persistent one-alert-per-episode overheat
**Files**: `BatteryCheckWorker.kt:48-50`, `HealthViewModel.kt:activeOverheatAlert`, new `OverheatAlertStore` (Room or DataStore, prefer `HealthReport`/prefs)
- Worker checks persisted `lastAlertDayKey + tempBand` before `notify()`; UI dismiss persists day (not in-memory `_dismissedAlertDay` only). Episode key = `dayKey + charging spike band`.
- Tests: double-trigger same day → 1 notification; dismiss → silent; new day → allowed.
- Verify: simulate 42°C charging twice → single alert.

### Task 8 — Strip CallBlocker leftovers (last)
**Files**: `UnjunkifyApp.kt`, `AppModule.kt`, `CallBlockerDatabase`, `AllowedContact`, `WhitelistScreen`, `CallLog*`, `AllowedNumberCache`, `ContactSync*` mirrors
- Remove CallBlocker DB/cache/mirrors from `UnjunkifyApp` (keep `UnjunkifyDatabase` only); remove dead DI bindings; keep `CallBlockerApp` composable only if needed for legacy entry (else delete + update callers). No behavior change to Clean/Health.
- Tests: full `./gradlew :androidApp:assembleDebug` + `:androidApp:testDebugUnitTest`.
- Verify: no `com.callblocker` imports under `com.unjunkify` runtime path; APK shrinks.

### Task 9 — Final gate (no code, verification only)
- `./gradlew :androidApp:assembleDebug`, `:androidApp:testDebugUnitTest`, airplane-mode checklist (scan→exempt→delete→health→worker), `grep -r INTERNET/MANAGE_EXTERNAL` negative check.
