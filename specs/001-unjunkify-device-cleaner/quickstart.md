# Quickstart: Validate Unjunkify Plan

Validates spec + plan without full implementation.

## Prerequisites

- Reference checkout: `call-blocker/` builds (`./gradlew :androidApp:assembleDebug`, JDK 21, Android SDK 36).
- Device/emulator Android 10+ with photos + some large files.

## Validate spec

1. Open `specs/001-unjunkify-device-cleaner/spec.md` — confirm US-1..US-5 flows are testable offline.
2. Open `checklists/requirements.md` — all 16 pass.

## Validate plan artifacts

1. `plan.md` — Technical Context matches CallBlocker stack; gates PASS.
2. `research.md` — 6 decisions recorded with rationale + alternatives.
3. `data-model.md` — 4 tables map to spec Key Entities.
4. `contracts/README.md` — 5 interfaces; workers read-only enforced.

## Manual smoke (post-scaffold, before tasks)

- `./gradlew :androidApp:assembleDebug` passes with renamed `com.unjunkify` namespace.
- Airplane mode: scan completes, exempt add/remove persists across restart, Health tab shows battery/temp.
- Deny optional usage permission: Health still shows battery/temp, no crash (FR-009).

## Expected outcomes

- SC-002: scan <30s on mid-range 50%-full device.
- SC-005: zero network requests (verify via offline test).
- SC-006: exempt items never deleted in test run.

Next: `$speckit-tasks` → `tasks.md`, then `$speckit-implement`.
