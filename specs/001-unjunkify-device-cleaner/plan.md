# Implementation Plan: Unjunkify Device Cleaner

**Feature**: `specs/001-unjunkify-device-cleaner/spec.md`
**Fork source**: `call-blocker/` (`com.callblocker`, Room v3, 2-tab `CallBlockerApp`, 6h `ContactSyncWorker`)
**Target**: `unjunkify/` (`com.unjunkify`), same KMP stack, offline-first
**Date**: 2026-10-01

## Technical Context

- Language: Kotlin 2.4.10 (multiplatform, `composeApp/build.gradle.kts:12-55` pattern)
- UI: Compose Multiplatform 1.11.1, Material 3 Dynamic Color, 2-tab scaffold (Clean / Health) cloned from `composeApp/.../App.kt:43-148`
- Platforms: Android 10+ (API 29) min, target SDK 36; iOS 15+ shared-framework only (no cleaner UI v1)
- Persistence: Room 2.8.4 KMP + SQLite bundled, KSP (`kspCommonMainMetadata`, `kspAndroid`), schema dir `composeApp/schemas/`
- Background: WorkManager 2.10.0, unique periodic `UnjunkifyHealthCheck` every 6h, `setRequiresBatteryNotLow(true)` (mirrors `MainActivity.kt:100-112`)
- DI: Koin (`koin.core`, `koin.compose`, `koin.android`), `AppModule`-style graph + `ViewModel` by `viewModel()`
- State: `UiState<Loading|Success|Error>` + `StateFlow` + `WhileSubscribed(5000)` (mirrors `CallBlockerViewModel.kt:35-65`)
- Android platform APIs: `StorageStatsManager`, `MediaStore`, `BatteryManager`/`ACTION_BATTERY_CHANGED`, `PackageManager`/`PACKAGE_USAGE_STATS` (optional, graceful degrade)
- Permissions v1: `POST_NOTIFICATIONS` (only for critical alerts), no `INTERNET`, no `READ_CONTACTS`, no `CallScreeningService` role
- Build: Gradle 9.1.0 + AGP 9.0.1, JDK 21, `assembleDebug` gate
- Testing: JUnit + `kotlinx.coroutines.test`, Compose UI test, `androidTest` runner (mirrors `androidApp/build.gradle.kts:88-94`)

Unknowns: none blocking — all resolved in `research.md`.

## Constitution Check

No `.specify/memory/constitution.md` found — fallback to CallBlocker principles (inferred from `call-blocker/README.md:3-14`, `requirements.md`):

- [x] Offline-first, privacy-first: no `INTERNET` permission, zero telemetry — enforced in manifest + `plan` gate.
- [x] Suggest-and-confirm only: no auto-delete (spec FR-011). Worker never deletes.
- [x] Minimize permissions: each permission justified in UI, denial degrades gracefully (spec FR-009).
- [x] Small testable units: scanner, scorer, exempt-repo independently testable; no God files.

Gate result: PASS. No violations requiring justification.

## Phase 0: Research (see `research.md`)

- Storage categorization: `StorageStatsManager` + `MediaStore` grouping, estimate-only sizes.
- Battery attribution: `BatteryManager` + usage heuristics, not exact mAh.
- Health score: 0–100 weighted (storage 40 / drain 35 / overheat 25).
- Exempt model: direct port of `AllowedContact(source=MANUAL/SEED/SYSTEM)` → `ExemptEntry`.

## Phase 1: Design (see `data-model.md`, `contracts/`, `quickstart.md`)

- `UnjunkifyDatabase` v1: `junk_snapshot`, `exempt_entry`, `battery_event`, `health_report` (replaces `allowed_contacts` + `call_log` from `CallBlockerDatabase.kt:9-13`).
- Common: `HealthScoreCalculator`, `ExemptRepository`, `HealthReportRepository` (ports of `ContactRepository`, `AllowedNumberCache`, `ContactSyncPlanner`).
- Android-only: `StorageScanWorker`, `BatteryMonitor`, `StorageScanner` impls behind common `expect/actual` or interface + androidMain impl.
- UI: `UnjunkifyApp` (Clean/Health tabs), `CleanViewModel` (port of `CallBlockerViewModel`), `HealthViewModel` (new).

## Post-design Constitution Re-check

- [x] No network paths added; workers use only on-device APIs.
- [x] Deletion only via explicit UI confirm → ViewModel → repo; workers read-only.
- [x] Exempt deletes blocked at repo layer + UI filter.

## Risks

- `PACKAGE_USAGE_STATS` friction → make optional, Health tab works without it (shows battery/temp only).
- Duplicate/similar photo grouping cost → cap scan to 10k newest media v1, 30s budget per SC-002.
- OEM battery kill → WorkManager `KEEP` + one-time resync on app open (same as CallBlocker pattern).
