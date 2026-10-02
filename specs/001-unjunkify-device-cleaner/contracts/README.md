# Contracts: Unjunkify Device Cleaner

Common interfaces (commonMain) with androidMain implementations. Workers read-only; mutations via repos after UI confirm.

## StorageScanner

```kotlin
interface StorageScanner {
  suspend fun scan(): List<JunkSnapshot> // grouped, exempt NOT yet filtered
  fun categories(): Set<JunkCategory>
}
```

- Impl `AndroidStorageScanner(storageStatsManager, mediaStore)`; test fake returns fixed sizes.
- Post-condition: every item has `estimatedBytes >= 0` and human-readable `label`.

## ExemptRepository

```kotlin
interface ExemptRepository {
  fun exemptIdsFlow(): Flow<Set<String>>
  suspend fun allOnce(): List<ExemptEntry>
  suspend fun add(entry: ExemptEntry)
  suspend fun removeById(targetId: String)
}
```

- Scanner/UI MUST filter via exempt cache first, Room second (CallBlocker `AllowedNumberCache` pattern).

## BatteryMonitor

```kotlin
interface BatteryMonitor {
  fun eventsFlow(): Flow<BatteryEvent>
  suspend fun history24h(): List<BatteryEvent>
}
```

- Impl via `BatteryManager` + optional `UsageStats`; absent permission → events with `consumerId=null`.

## HealthScoreCalculator (pure)

```kotlin
fun healthScore(storageUsedPct: Int, bgDrainHours: Int, overheat7d: Int): Int
```

- Deterministic, 0–100, no platform APIs. Unit tests cover boundaries.

## UI contract

- `CleanScreen(viewModel)`: scan CTA, category groups with sizes, multi-select, exempt action, confirm-delete dialog, reclaimed banner.
- `HealthScreen(viewModel)`: score ring, battery timeline, top consumers, charging history, rescan CTA.
- Navigation: bottom bar `Clean | Health` (port of `App.kt:95-127`).
