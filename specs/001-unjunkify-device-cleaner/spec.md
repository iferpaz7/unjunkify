# Feature Specification: Unjunkify Device Cleaner

**Feature**: Unjunkify — offline-first phone health cleaner (fork of CallBlocker concept)
**Directory**: `specs/001-unjunkify-device-cleaner`
**Source reference**: `call-blocker/` (Compose Multiplatform + Room + WorkManager, offline-first, no internet)
**Status**: Draft for planning (`$speckit-plan` next)

## Overview

Users with slow, hot, or full phones need a private cleaner that reclaims space and improves battery health without ads, accounts, or cloud uploads. Existing cleaners (CCleaner, Files by Google, Zero Cleaner) require network access and collect data.

Unjunkify is a CallBlocker-sibling: same offline-first, privacy-first philosophy applied to device health instead of call blocking. It scans storage junk, flags battery-draining background behavior, warns about overheating / bad charging patterns, and lets users exempt apps and files. All analysis stays on-device.

Name validation (2026-10-01, web search Play Store / App Store): `Unjunkify` has zero hits in the phone-cleaner category. Rejected: `Limpify`, `Tidify`, `Prunify`, `Cullify`, `Vivify`, `Cleanify`, `Zero Cleaner`, `Slimify`, `Hushify` (all taken or confusable).

## User Scenarios & Testing

### US-1: Reclaim space in under 3 minutes
Actor: phone owner with "storage almost full" warning.
Flow: open app → run health scan → see grouped results (cache junk, large videos, duplicates/screenshots, leftover installers) with sizes → select groups → confirm clean → see reclaimed space and updated health score.
Acceptance: user can go from warning to reclaimed space without leaving the app; exempted items are never listed for deletion.

### US-2: Find what drains battery
Actor: user whose phone dies by afternoon.
Flow: open Health tab → see battery timeline (drain rate, temperature spikes, top background consumers in last 24h) → tap an entry → see explanation + suggested action (restrict background, lower brightness, close camera) → apply or dismiss.
Acceptance: user can name the top drain contributor without technical knowledge.

### US-3: Prevent overheating / bad charging
Actor: user who charges overnight and games while charging.
Flow: app records charging sessions and temperature warnings → shows gentle alert ("phone hot 42°C while charging") → suggests pause-intensive-apps or unplug habit → user acknowledges.
Acceptance: user receives at most one alert per episode, with a clear dismiss.

### US-4: Exempt what matters
Actor: privacy-conscious user.
Flow: from any result, tap Exempt → item/app moves to exempt list → future scans skip it → user can remove exemption later.
Acceptance: exempted items never reappear in results unless exemption is removed.

### US-5: Periodic background check
Actor: busy user who forgets maintenance.
Flow: app runs a periodic health check about every 6 hours when battery is not low → updates dashboard silently → no notification unless new critical issue (e.g., >1 GB new junk or repeated overheat).
Acceptance: dashboard is fresh on open without manual scan.

## Functional Requirements

- FR-001: System MUST provide a one-tap health scan grouping results into: temporary/cache junk, large files, duplicates/screenshots, leftover installers.
- FR-002: Each result MUST show estimated size and safe-to-remove explanation before deletion.
- FR-003: User MUST be able to exempt individual files, folders, or apps; exempt entries MUST be excluded from all future scans until removed.
- FR-004: System MUST show battery status: current level, temperature, drain rate, and top background consumers for the last 24 hours.
- FR-005: System MUST record charging sessions and temperature warnings in a local history viewable by date.
- FR-006: System MUST compute a simple health score (0–100) from storage pressure, battery drain, and overheat frequency; score MUST update after each scan or event.
- FR-007: System MUST run periodic background checks (~every 6 hours, only when battery not low) and refresh the dashboard without user action.
- FR-008: System MUST work fully offline; no account, no ads, no telemetry, no network calls for core features.
- FR-009: System MUST request only the minimum device permissions needed for scanning and MUST explain each request in plain language; denial MUST degrade gracefully (skip that category, keep rest working).
- FR-010: System MUST keep a local action log (what was cleaned, when, how much reclaimed) with undo-safe behavior: never delete exempt, system-critical, or unconfirmed items.
- FR-011: Deletion MUST require explicit user confirmation; automatic deletion without confirmation is out of scope for v1.

## Success Criteria

- SC-001: New users complete first scan-to-cleanup in under 3 minutes.
- SC-002: 95% of manual scans finish in under 30 seconds on a mid-range device with 50% storage used.
- SC-003: Users reclaim at least 200 MB on first cleanup on devices with <20% free space.
- SC-004: 90% of test users can correctly name their top battery drain contributor after viewing the Health tab.
- SC-005: Zero network requests during core scan/clean flows (verifiable in offline airplane-mode test).
- SC-006: Exempted items have 0% accidental deletion rate in acceptance testing.

## Key Entities

- `JunkItem`: path/label, category (cache, large, duplicate, installer), estimated size, last-seen date.
- `ExemptEntry`: target (file/folder/app id), display name, creation date, reason.
- `BatteryEvent`: timestamp, level %, temperature °C, charging state, foreground/background consumer id.
- `HealthReport`: date, health score 0–100, reclaimed bytes, issue counts.

## Assumptions

- Android 10+ primary; shared business logic portable, iOS limited to shared logic only (mirrors CallBlocker constraint).
- Suggest-and-confirm cleaning only for v1; no auto-delete, no root required.
- Storage categories use on-device stats APIs; exact byte counts are estimates.
- Battery consumer attribution is heuristic (foreground time + background activity), not exact mAh.
- Text language: English first, Spanish second (Ecuador context like CallBlocker).

## Edge Cases

- Permission denied → skip category, show inline explanation, keep other categories working.
- Empty/low-junk phone → show healthy state, not empty error.
- Overheat during scan → pause scan, resume when cool, inform user.
- Duplicate detection with edited/cropped photos → group as "similar", require per-item confirm.
- System reboots / revoked permissions → dashboard shows stale-data badge with rescan CTA.

## Out of Scope (v1)

- Automatic deletion, antivirus, VPN, RAM boosters, cloud backup.
- iOS full-feature cleaner UI; iOS gets shared scoring logic only.
- Monetization, accounts, analytics dashboards.

## Dependencies

- Access to on-device storage stats, media library, battery status, app usage stats (all user-granted).
- Design sibling to CallBlocker: two-tab app (Clean / Health), glass Material 3 theme, search + exempt management.
