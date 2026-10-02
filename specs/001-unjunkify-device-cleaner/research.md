# Research: Unjunkify Device Cleaner

**Feature**: `specs/001-unjunkify-device-cleaner/spec.md`
**Date**: 2026-10-01

## Decision 1: Storage categorization without root

- Decision: Use `StorageStatsManager` for aggregate + `MediaStore` scan for large/duplicate/screenshot/installer grouping. Sizes are estimates.
- Rationale: Works on API 29+ without root, matches offline constraint, maps to FR-001/FR-002.
- Alternatives considered: `UsageStats` for file access (rejected — wrong API), SAF folder walk only (rejected — slow, misses categories), root `du` (rejected — out of scope).

## Decision 2: Battery attribution heuristic

- Decision: `BatteryManager` (level/temp/charging) + foreground/background time heuristic for top consumers over 24h. Label as estimate in UI.
- Rationale: Exact mAh per app requires privileged APIs; heuristic satisfies FR-004 test (user can name top drainer) without extra permissions.
- Alternatives considered: `BatteryStats` hidden API (rejected — unstable), `PACKAGE_USAGE_STATS`-only ranking (accepted as optional enhancement, not required).

## Decision 3: Optional PACKAGE_USAGE_STATS

- Decision: Request only if user opens Health details; fully optional. Denial → show battery/temp/charging only.
- Rationale: Satisfies FR-009 graceful degrade; avoids onboarding friction that CallBlocker has with `READ_CONTACTS` (`ContactSyncWorker.kt:24-30` early-return pattern reused).
- Alternatives considered: Mandatory at onboarding (rejected — drop-off), no usage stats at all (rejected — weakens US-2).

## Decision 4: Health score formula

- Decision: 0–100 = 100 − (storage_pressure×40 + drain×35 + overheat×25), clamped. Storage pressure = used% over 80%; drain = abnormal background hours; overheat = warning count/7d.
- Rationale: Simple, explainable, testable in commonMain without Android APIs (pure function like `EcuadorPhoneUtils` / `ContactSyncPlanner`).
- Alternatives considered: ML model (rejected — overkill, offline cost), single-metric score (rejected — hides trade-offs).

## Decision 5: Exempt model ports allowlist

- Decision: Port `AllowedContact(normalizedNumber, displayName, source)` + `AllowedNumberCache.replaceAll` + `ContactSyncPlanner.planSync` to `ExemptEntry(targetId, label, source)` + in-memory cache + stale-exempt pruning.
- Rationale: Proven CallBlocker hot-path (`CallBlockerViewModel.kt:99-107` best-effort cache refresh, service falls back to DB). Scanner checks exempt-cache first (<5 ms), falls back to Room.
- Alternatives considered: No cache (rejected — scan jank), DataStore only (rejected — loses Room query + KMP reuse).

## Decision 6: Workers are read-only

- Decision: `StorageScanWorker` + `BatteryMonitor` only write snapshots/reports; never delete. Deletion lives in ViewModel → repo after explicit confirm.
- Rationale: Enforces FR-011, prevents background data loss, mirrors CallBlocker split (service reads cache/DB, UI mutates via repo).
