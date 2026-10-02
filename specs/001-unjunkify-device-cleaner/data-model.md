# Data Model: Unjunkify Device Cleaner

**Feature**: `specs/001-unjunkify-device-cleaner/spec.md` entities → Room v1 tables.

## ExemptEntry (port of `AllowedContact`)

- `targetId: String` PK — stable id (file hash / package name / folder URI).
- `label: String` — display name.
- `kind: Enum[CACHE_PATH, FILE, FOLDER, APP]` — what is exempted.
- `source: Enum[MANUAL, SEED, SYSTEM]` — MANUAL user-added, SEED preinstalled safe-list, SYSTEM discovered.
- Rules: exempt ids excluded from every scan result; delete-by-id allowed; `replaceAll(ids)` refreshes hot cache best-effort.
- Seed: common system paths (e.g., DCIM/Camera exempt by default? No — seed only critical OS dirs + backup dirs).

## JunkSnapshot

- `id: Long` auto PK, `scannedAt: Long` epoch.
- `category: Enum[TEMP_CACHE, LARGE_FILE, DUPLICATE, SCREENSHOT, INSTALLER]`.
- `pathOrKey: String`, `label: String`, `estimatedBytes: Long`, `count: Int` (1 or group size).
- Validation: `estimatedBytes >= 0`; group entries require per-item confirm in UI (spec edge case).
- Retention: keep last 30 snapshots for log view (FR-010); prune older in worker.

## BatteryEvent

- `id: Long` auto PK, `at: Long` epoch.
- `levelPct: Int 0–100`, `tempC: Float`, `charging: Boolean`, `consumerId: String?`, `consumerLabel: String?`.
- Rules: written by monitor only; UI aggregates 24h top consumers; at most 1 alert per overheat episode (dedupe key = day + consumer or temp band).

## HealthReport

- `id: Long` auto PK, `at: Long`, `score: Int 0–100`, `reclaimedBytes: Long`, `junkCount: Int`, `overheatCount: Int`.
- Computed by pure `HealthScoreCalculator(score inputs)` in commonMain — unit-testable without Android (like `ContactSyncPlannerTest`).

## Relationships / State

- Scan → `JunkSnapshot` rows → UI filters by `ExemptEntry.targetId` (via cache) → user confirms → delete action → new `HealthReport` + prune snapshots.
- `BatteryEvent` stream → 24h aggregation → Health tab + score input.
- No foreign keys v1 (avoid migration cost); joins in repo layer.
