# Unjunkify

Offline-first Android device cleaner. Scans storage junk, tracks battery health, and warns about overheating — all on-device, no internet, no accounts.

Two tabs:

- **Clean** — groups large files, screenshots, installers and content-hash duplicates; per-item confirm; exempt anything; delete goes through the Android system consent dialog, reclaimed bytes count only what was actually deleted.
- **Health** — battery level/temperature + 24h history, foreground-time ranking (optional usage-access opt-in), and a real health score from storage pressure, drain and overheat days.

## Principles

- Offline-first: zero network calls, no `INTERNET` permission.
- Suggest-and-confirm: background workers only scan and notify, they never delete.
- Minimum permissions with graceful degrade: denying media or usage access skips that category, the rest keeps working.
- Scoped Storage only: `MediaStore` + `StorageStatsManager`, no broad file access. Sizes are estimates and labeled as such.

## Stack

| Layer       | Technology                                                |
|-------------|-----------------------------------------------------------|
| UI          | Compose Multiplatform (Material 3)                        |
| Language    | Kotlin (multiplatform)                                    |
| Platforms   | Android 10+ (API 29)                                      |
| Persistence | Room (KMP) + SQLite Bundled                               |
| Background  | WorkManager (health/storage checks every 6 h)             |
| Build       | Gradle + AGP, JDK 21                                      |

## Build

```bash
# Unit tests
./gradlew :androidApp:testDebugUnitTest

# Debug APK (installs as com.unjunkify)
./gradlew :androidApp:assembleDebug
```

## Versioning

Releases are cut by pushing a tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.

- Tag builds run unit tests, assemble a release APK and publish a GitHub Release with the APK attached.
- Pushes to `main` and PRs only verify (tests + build) and keep the APK as a workflow artifact.
- Signed release APKs need the keystore secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); without them the build falls back to debug signing.

## License

Apache 2.0
