# Pomegranate — Architecture

> Derived from `.taskmaster/docs/prd.md`. This file holds the *stable*
> architectural decisions and is loaded into every Claude session via
> CLAUDE.md. When the PRD changes, update **this** file — not `CLAUDE.md`.
> Volatile detail (full data model, phasing, open questions) stays in the PRD;
> this is the always-loaded summary.

## What we're building
An Android app that shows a person exactly where each photo/video lives
(**cloud-only / device-only / both**, Google Photos vs this phone), pulls
everything cloud-only into a destination they choose (internal, SD card, USB),
proves every copy with a verified manifest, and then disconnects from Google
leaving no trace. Named for Persephone's pomegranate seeds: hospitality that
quietly revokes your autonomy. v1 is phone-only and Google-Photos-only.

## Repo layout
- `app/` — the single Android module (`com.gankdat.pomegranate`).
  `src/main/kotlin/com/gankdat/pomegranate/{census/device,census/cloud,reconcile,transfer,trust,ui,data}`,
  `src/test/kotlin` (JUnit 4 unit tests), `src/main/res`.
- `gradle/libs.versions.toml` (all versions), `gradle/verification-metadata.xml`
  (checksums), `gradle/wrapper` (pinned Gradle).
- `docs/` — this file, setup docs. `.taskmaster/` — PRD, research notes, tasks.
- `.devcontainer/` — arm64 Ubuntu + JDK 17 + Android SDK (x86_64 tools under
  Rosetta).

## Stack
Kotlin 2.4 · Jetpack Compose + Material 3 · AndroidX: Room (local DB),
DataStore (settings), WorkManager (background transfers), Activity/Lifecycle,
Browser (Custom Tabs) · AGP 9.4 / Gradle 9.8 / JDK 17 · minSdk 33,
compileSdk 37. Dependency policy: platform + AndroidX only (JUnit 4 for tests).

## Backend & data
- **No backend.** Everything runs on the device. No analytics or telemetry.
- Local Room DB: `device_item`, `cloud_item`, `media_group` (sibling files:
  RAW+JPEG, live/motion photo halves, original+edited), `match` (with
  confidence + evidence), `transfer_job` (with fidelity + verification),
  `picker_round`, `origin_rule`. DataStore: destination root URI, layout,
  concurrency, Wi-Fi-only, auto-disconnect.
- Device census from `MediaStore` across all volumes; fingerprint = filename +
  size + date-taken + dimensions; SHA-256 only for ambiguous matches and every
  pulled file.
- Pulled files are written via SAF to
  `<root>/Pomegranate/YYYY/YYYY-MM/<Origin>/` where Origin is inferred
  (learned folder↔filename patterns from the device, then built-in app
  patterns; unknown → month folder), then registered with MediaStore.
- Secrets (OAuth tokens) encrypted with an Android Keystore AES key in
  app-private storage; wiped on Disconnect. `allowBackup=false` plus
  `fullBackupContent` / `dataExtractionRules` excluding everything from cloud
  backup and device transfer.

## External services
- **Google OAuth 2.0** (PKCE, public Android client, Custom Tabs, no secret):
  `accounts.google.com`, `oauth2.googleapis.com` (token + revoke).
- **Google Photos Picker API** (`photospicker.googleapis.com`, scope
  `photospicker.mediaitems.readonly`): the only sanctioned way to see a
  user's library since the Library API scopes were removed (2025-03-31).
  Limits that shape the design: user-driven selection, ≤2,000 items per
  session, base URLs valid 60 min, photos lose GPS, videos are transcoded.
  Every pulled item carries a fidelity badge (Original / Location stripped /
  Transcoded video). The client sits behind an interface so a Takeout-indexed
  source can be added later without touching reconcile/transfer.
- No API can delete from Google Photos; the app only guides manual clean-up
  after copies are verified.
- Owner must create a Google Cloud project with the Picker API enabled and an
  Android OAuth client (package + signing SHA-1); the client id is supplied
  via `local.properties` (gitignored), never committed.

## Compliance & accessibility
- GDPR/UK-GDPR audience: on-device processing, no data leaves the phone except
  to Google; Disconnect = revoke + wipe; nothing to erase server-side.
- Least-privilege permissions: `INTERNET`, `READ_MEDIA_IMAGES/VIDEO` (+
  Android 14 user-selected handling); SAF for writes. No
  `MANAGE_EXTERNAL_STORAGE`.
- Material 3, dark mode, dynamic colour, TalkBack content descriptions, large
  touch targets. English-only UI in v1.

## Out of scope for v1 (do not scope-creep without re-baselining)
- Takeout-indexed full-fidelity mode (originals, GPS, albums, real device
  folders from `.supplemental-metadata.json`).
- Desktop app / browser extension automating Takeout downloads.
- NAS / network destinations (SMB, WebDAV).
- iOS.
- Other providers (iCloud, OneDrive, Dropbox) and other data types.
- Deleting or modifying anything in Google Photos (impossible via API anyway)
  or deleting device files.
- Play Store release work (OAuth verification, store assets) until the app
  proves itself as a sideloaded personal tool.
