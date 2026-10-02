# Tasks: Unjunkify Device Cleaner

**Feature**: `specs/001-unjunkify-device-cleaner/spec.md` | **Plan**: `specs/001-unjunkify-device-cleaner/plan.md`
**Fork source**: `call-blocker/` → **Target**: `unjunkify/` (`com.unjunkify`)
**Stack**: Kotlin KMP + Compose M3 + Room + WorkManager, offline-first, no `INTERNET`

## Phase 1: Setup (fork init)

- [X] T001 Copy `call-blocker/` to `unjunkify/` excluding `build/` `.gradle/` `.idea/` in `unjunkify/`
- [X] T002 [P] Rename package `com.callblocker` to `com.unjunkify` in `unjunkify/settings.gradle.kts`
- [X] T003 [P] Update namespace/applicationId to `com.unjunkify` in `unjunkify/androidApp/build.gradle.kts`
- [X] T004 [P] Update shared namespace to `com.unjunkify.shared` in `unjunkify/composeApp/build.gradle.kts`
- [X] T005 Strip call-blocking manifest entries and `READ_CONTACTS` in `unjunkify/androidApp/src/main/AndroidManifest.xml`
- [X] T006 Delete call-only sources in `unjunkify/androidApp/src/main/kotlin/com/call-blocker/service/CallBlockerScreeningService.kt`
- [X] T007 Delete call role helper in `unjunkify/androidApp/src/main/kotlin/com/call-blocker/CallScreeningRole.kt`

## Phase 2: Foundational (blocking)

- [X] T008 Create Room entities/DAOs for exempt, snapshot, battery, report in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/data/`
- [X] T009 Create `UnjunkifyDatabase` v1 with seed callback in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/data/UnjunkifyDatabase.kt`
- [X] T010 [P] Implement `ExemptRepository` + in-memory exempt cache in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/data/repository/ExemptRepository.kt`
- [X] T011 [P] Implement pure `healthScore()` scorer in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/utils/HealthScoreCalculator.kt`
- [X] T012 Wire Koin graph for repos, scanner interfaces, ViewModels in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/di/AppModule.kt`
- [X] T013 Port 2-tab scaffold to Clean/Health in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/App.kt`

## Phase 3: US-1 — Reclaim space [US1]

**Goal**: scan → grouped results with sizes → confirm → reclaimed banner. **Independent test**: airplane mode, seeded 500 MB junk, exempt item never listed, cleanup <3 min.

- [X] T014 [P] [US1] Implement `AndroidStorageScanner` via StorageStatsManager/MediaStore in `unjunkify/composeApp/src/androidMain/kotlin/com/unjunkify/scanner/AndroidStorageScanner.kt`
- [X] T015 [US1] Implement `CleanViewModel` scan/select/confirm flow in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/CleanViewModel.kt`
- [X] T016 [US1] Build `CleanScreen` groups + confirm dialog + reclaimed banner in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/CleanScreen.kt`
- [X] T017 [US1] Persist scan results and post-clean `HealthReport` in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/data/repository/HealthReportRepository.kt`

## Phase 4: US-4 — Exempt management [US4]

**Goal**: exempt from any result, persistent skip, removable. **Independent test**: exempt file → rescan → absent; unexempt → reappears.

- [X] T018 [P] [US4] Add exempt/remove actions to scan rows in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/CleanScreen.kt`
- [X] T019 [US4] Build exempt list screen with search + remove in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/ExemptScreen.kt`
- [X] T020 [US4] Enforce exempt filter in scanner pipeline in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/utils/ExemptFilter.kt`

## Phase 5: US-2 — Battery drain [US2]

**Goal**: 24h timeline + top consumers + plain-language actions. **Independent test**: without usage permission shows level/temp; with permission shows ranked consumer.

- [X] T021 [P] [US2] Implement `AndroidBatteryMonitor` via BatteryManager in `unjunkify/composeApp/src/androidMain/kotlin/com/unjunkify/monitor/AndroidBatteryMonitor.kt`
- [X] T022 [US2] Implement optional usage-stats consumer ranking in `unjunkify/androidApp/src/main/kotlin/com/unjunkify/monitor/UsageConsumerRanker.kt`
- [X] T023 [US2] Implement `HealthViewModel` timeline aggregation in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/HealthViewModel.kt`
- [X] T024 [US2] Build `HealthScreen` score ring + timeline + actions in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/HealthScreen.kt`

## Phase 6: US-3 — Overheat/charging [US3]

**Goal**: session history + one alert per episode with dismiss. **Independent test**: simulate 42°C charging → single alert; dismiss → silent.

- [X] T025 [US3] Record charging sessions and temp warnings in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/data/repository/BatteryRepository.kt`
- [X] T026 [US3] Add overheat dedupe and alert card in `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/HealthScreen.kt`

## Phase 7: US-5 — Periodic check [US5]

**Goal**: 6h silent refresh, notify only on critical. **Independent test**: trigger worker → dashboard fresh, no notification when healthy.

- [X] T027 [US5] Implement read-only `StorageScanWorker` in `unjunkify/androidApp/src/main/kotlin/com/unjunkify/sync/StorageScanWorker.kt`
- [X] T028 [US5] Implement read-only `BatteryCheckWorker` in `unjunkify/androidApp/src/main/kotlin/com/unjunkify/sync/BatteryCheckWorker.kt`
- [X] T029 [US5] Schedule one-time + 6h `KEEP` work with battery-not-low in `unjunkify/androidApp/src/main/kotlin/com/unjunkify/MainActivity.kt`

## Phase 8: Polish

- [X] T030 [P] Add launcher icons and rename strings to Unjunkify in `unjunkify/androidApp/src/main/res/`
- [X] T031 [P] Add healthy/empty/stale-data states across `unjunkify/composeApp/src/commonMain/kotlin/com/unjunkify/ui/`
- [X] T032 Run `quickstart.md` validation and `./gradlew :androidApp:assembleDebug` in `unjunkify/`

## Dependencies

- Setup (T001–T007) → Foundational (T008–T013) → US-1 (T014–T017) → US-4 (T018–T020) → US-2 (T021–T024) → US-3 (T025–T026) → US-5 (T027–T029) → Polish (T030–T032)
- US-2/US-3 need Foundational DB; US-5 needs US-1 scanner + US-2 monitor; US-4 UI can parallel US-2 after Foundational.

## Parallel examples

- After Setup: T002 + T003 + T004 (different gradle files).
- Foundational: T010 + T011 (repo vs pure scorer).
- US-1: T014 (androidMain) + Health-side T021 (different files) if staffing allows.
- Polish: T030 + T031 (res vs UI states).

## MVP scope

- T001–T017 + T020 (setup + foundation + US-1 with exempt filtering). Demo: offline scan → confirm → reclaimed + score. US-4 UI, US-2/3/5 follow incrementally.
