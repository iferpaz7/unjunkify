# Unjunkify Wiki

Offline-first Android device cleaner (`com.unjunkify`).

## What it does

- **Clean tab** — storage scan grouped into large files, screenshots, installers and duplicates (detected by content hash, never by size alone). Exempt list is always respected, including a re-check after the system delete dialog. The reclaimed-bytes counter only counts files actually deleted.
- **Health tab** — battery snapshot (level, temperature, charging) + 24h history, foreground-time app ranking behind an optional usage-access grant, and a health score computed from real inputs: storage pressure (`StorageStatsManager`), 24h drain delta and overheat days.
- **Workers** — `StorageScanWorker` and `BatteryCheckWorker` run every 6 hours (battery-not-low). They scan and notify only; deletion happens exclusively from the UI with explicit confirmation. Overheat alerts are deduplicated to one per episode (day + temperature band), persisted across restarts.

## Permissions

| Permission | Scope | If denied |
|---|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (API 33+) or `READ_EXTERNAL_STORAGE` (≤32) | Scan photos/videos | Those categories are skipped, the rest works |
| Usage access (Settings opt-in) | Foreground-time ranking | Ranking hidden, battery/temp still shown |
| `POST_NOTIFICATIONS` | Overheat/scan alerts | No notifications |

No `INTERNET`, no `MANAGE_EXTERNAL_STORAGE`, no `QUERY_ALL_PACKAGES`.

## Versioning

Releases are cut with tags (`vX.Y.Z`) — see README. No tag, no release.
