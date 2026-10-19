<context>
# Overview

**Pomegranate** is an Android app that helps a person see exactly where their photos and videos live (on this phone, in Google Photos, or both), pull everything that is cloud-only down to storage they control, prove the copies are good, and then disconnect from Google leaving no trace.

It is named after the pomegranate seeds Hades gives Persephone: hospitality that is generous and convenient on the way in, and quietly revokes your autonomy; you only discover the trap in the effort it takes to leave. Consumer cloud storage works the same way. Google Photos makes backing up effortless and leaving miserable: there is no "download everything", the mobile app does not tell you what is cloud-only, Takeout produces dozens of 2 GB archives through a flaky browser flow, and third-party tools either lost their API access in 2025 or keep a standing connection to your account after the job is done.

Pomegranate's v1 does one job well, from the phone: **a clear census of cloud vs device, a reliable pull of what is only in the cloud into a destination the user chooses (including an SD card), and a clean exit.** The defining quality of the product is that it never compromises user data: everything runs on the device, nothing is sent anywhere except to Google's own APIs, the Google connection lives only as long as the job, and no file is ever reported as saved until it has been verified.

**Who it's for:** ordinary phone owners (not sysadmins) who have years of photos in Google Photos and want them back under their own control, on their phone, an SD card, and later a NAS. First user is the project owner, on their own device.

# Core Features

## 1. Device census
- **What:** Scans the phone's media store (internal and removable storage) and builds a local index of every photo and video: path, folder, size, type, timestamps, dimensions/duration, and a fingerprint.
- **Why:** The device side is the half of the picture Google never shows clearly. It also makes the app useful before any Google account is connected.
- **How:** `MediaStore` queries for images and videos (all volumes), grouped by bucket (folder). Fingerprint = filename + size + date-taken + dimensions, with a content hash computed lazily for verification and ambiguous matches. Stored in an on-device SQLite database; incremental re-scans.

## 2. Cloud census via the Google Photos Picker API
- **What:** The user connects their Google account and selects media in Google's own picker; Pomegranate records what was selected as its index of "what's in the cloud". Large libraries are covered in repeated picker rounds (the Picker caps a session at 2,000 items).
- **Why:** Since March 2025 this is the only Google-sanctioned way a third-party app can see a user's library at all. It works the moment the account is connected, with no Takeout and no waiting.
- **How:** OAuth 2.0 (PKCE, Custom Tabs, no backend, scope `photospicker.mediaitems.readonly`), Picker sessions created and polled with the platform HTTP stack, media items listed with pagination and stored locally. The UI tracks coverage ("you've indexed 6,400 items in 4 rounds") and makes it obvious that the cloud index is only as complete as the user's selections.

## 3. Reconciliation: cloud-only / device-only / both
- **What:** Matches cloud items to device items and assigns every item one of three states, with a confidence level and the evidence shown.
- **Why:** This is scope point 2: the single question the Google Photos app refuses to answer.
- **How:** Deterministic matching on filename + capture time + dimensions (and size where available); hash confirmation when ambiguous. Groups sibling files that are one piece of media (RAW+JPEG, HEIC+MOV live photos, Pixel motion photos, original+edited) so the user sees "one memory, three files".

## 4. Library view
- **What:** Browse by status, by device folder, by month; counts and storage totals per group; per-item detail showing where it lives, the matching evidence, its sibling files, and its fidelity badge.
- **Why:** Scope points 4 and 5: an average user must be able to understand the relationship between folders on the phone and what is in the cloud, which neither Google Photos nor PhotoSync makes legible.
- **How:** Jetpack Compose UI backed by the local database; thumbnails from the device for local items and from the Picker base URLs for cloud-only items.

## 5. Pull: download cloud-only media to a destination the user chooses
- **What:** Queue every cloud-only item (or a selection) for download into a destination root the user picks via the system folder picker, which may be internal storage, an SD card, or USB storage. Bounded concurrency (default 3), automatic resume after network loss or app restart, Wi-Fi-only and battery options, progress with a truthful "what's left" list.
- **Why:** Scope points 1 and 3, done from the phone: reliable bulk download with all formats kept and nothing silently dropped.
- **How:** A persistent job queue executed by WorkManager; files written through the Storage Access Framework into the chosen root; sibling files downloaded together; every file verified (size, and hash where the source provides one or by re-read) before it is marked done; then the file is registered with the media store so gallery apps see it.
- **Folder layout with origin inference:** pulled items land in `<root>/Pomegranate/YYYY/YYYY-MM/<Origin>/`, where *Origin* is the app the media came from (WhatsApp, Telegram, Screenshots, Camera, …). The Picker does not report a device folder, so Origin is inferred: first from the user's own device census (the scanner learns which filename patterns live in which folders, e.g. `IMG-20230415-WA0012.jpg` ↔ `WhatsApp Images`), then from a built-in table of well-known app naming patterns. Items with no confident origin go directly in `YYYY-MM/`. Every inference is shown on the item and can be overridden before the pull. The Takeout-indexed mode (future) replaces inference with the real original folder name.
- **Fidelity honesty:** the Picker's download endpoints keep photo EXIF except GPS and return *transcoded* video rather than the original. Each pulled item carries a badge (Original / Location stripped / Transcoded video) and the UI explains it before the first pull. Full-fidelity originals arrive in phase 2 via the Takeout-indexed mode.

## 6. Proof of copy and manifest
- **What:** A verifiable record of every file pulled (path, size, hash, source id, fidelity), exportable as CSV/JSON to the destination root, and a "Safe to clean up" summary the user can rely on before removing anything from Google.
- **Why:** The user is about to delete the only other copy. They need evidence, not a progress bar.
- **How:** Generated from the verified-job table; no file appears without a passing verification record.

## 7. Leave no trace
- **What:** A one-tap **Disconnect** that revokes the Google token with Google, wipes tokens, the cloud index and cached thumbnails, and walks the user through confirming the removal in their Google Account permissions page. An optional "disconnect automatically when the pull completes" mode.
- **Why:** Scope point 6: the user should not have to trust that the app behaves after the job; the connection should simply not exist.
- **How:** Token revocation endpoint + Keystore-backed secret wipe + database drop; a guided checklist with deep links; a status screen that always shows whether a connection currently exists and what it can access.

## 8. Cloud clean-up guidance (no API exists for deletion)
- **What:** After verified copies exist, step-by-step guidance for deleting from Google Photos or turning off backup, written for an average user.
- **Why:** Google provides no API to delete or modify items, so the app cannot do it; the next best thing is to make the manual path safe and clear, grounded in the proof-of-copy record.

# User Experience

**Personas**
- *The leaver:* has 30–150k items in Google Photos, is near their storage quota, has tried Takeout and given up. Wants everything on an SD card and a NAS. Low tolerance for ambiguity; high anxiety about losing anything.
- *The checker:* unsure what is backed up and what isn't; wants a trustworthy map before deciding anything.

**Key flows**
1. **First run:** plain-language explanation of what the app does, what it never does (no servers, no analytics, only Google is contacted), and the three-state model. Pick a destination root (folder picker; SD card highlighted when present). Device census runs immediately and shows results. No Google connection required to reach this point.
2. **Connect & index:** connect Google → explain the picker-round model → open picker → return → index grows → status view updates. Repeat rounds until coverage satisfies the user.
3. **Review:** library view by status; drill into a folder or an item; understand evidence.
4. **Pull:** choose scope (everything cloud-only / a folder / a selection) → fidelity notice → start → background progress with notification → completion summary and manifest.
5. **Leave:** Disconnect; guided confirmation in Google Account; clean-up guidance for Google Photos.

**UI/UX considerations**
- Three states, three consistent colours/icons everywhere; never a fourth ambiguous state without an explanation.
- Every number is explained ("cloud-only: 1,204 items, 38 GB, across 3 picker rounds").
- Nothing destructive, ever: the app never deletes device files and cannot delete cloud files.
- Warn when the destination root is inside a folder Google Photos backs up (pulled files would be re-uploaded) and show how to exclude it.
- Material 3, dynamic colour, dark mode, TalkBack labels on all controls, large touch targets.
</context>
<PRD>
# Technical Architecture

**Platform & stack**
- Native Android, Kotlin 2.4, Jetpack Compose (Material 3), minSdk 30, target/compileSdk 37, AGP 9.4, Gradle 9.8, JDK 17.
- **Dependency policy (hard rule):** Android platform, Kotlin stdlib, and Google AndroidX/Jetpack only. No third-party libraries without the owner's explicit approval, each justified in writing. All artifacts pinned in `gradle/libs.versions.toml` and checksum-verified with Gradle dependency verification. Repositories limited to Google and Maven Central.
- **No backend.** The app talks only to `accounts.google.com`, `oauth2.googleapis.com` and `photospicker.googleapis.com` (plus the media base URLs those return). No analytics, crash reporting or update checks.

**Components (single module, package `com.gankdat.pomegranate`, feature-oriented packages)**
- `census/device`: MediaStore scanner, fingerprinting, incremental rescans.
- `census/cloud`: OAuth (PKCE + Custom Tabs + app-link redirect), Picker API client (sessions, polling, media item listing), cloud index.
- `reconcile`: matching engine, sibling grouping, state assignment, origin inference (learned folder↔filename patterns plus built-in app patterns).
- `transfer`: job queue, WorkManager workers, bounded-concurrency downloader using `HttpsURLConnection`, SAF writer, verifier, media store registration.
- `trust`: Keystore-backed secret store, revoke/wipe, connection status, manifest export.
- `ui`: Compose screens (Onboarding, Library, Item, Pull, Connection, Settings), ViewModels, navigation.
- `data`: Room database (AndroidX) and DataStore (AndroidX) for settings.

**Data model (core tables)**
- `device_item(id, volume, relative_path, bucket, display_name, mime, size, date_taken, date_modified, width, height, duration_ms, fingerprint, sha256?)`
- `cloud_item(id, picker_media_item_id, session_id, filename, mime, type, create_time, width, height, duration_ms, camera_make, camera_model, base_url, base_url_expires_at)`
- `media_group(id, kind: SINGLE|LIVE_PHOTO|MOTION_PHOTO|RAW_PAIR|EDIT_PAIR)` with membership rows for device and cloud items.
- `match(cloud_item_id, device_item_id, confidence: EXACT|LIKELY|AMBIGUOUS, evidence)`
- `transfer_job(id, cloud_item_id, state: QUEUED|RUNNING|VERIFYING|DONE|FAILED|PAUSED, destination_uri, bytes_expected, bytes_written, sha256, fidelity: ORIGINAL|GPS_STRIPPED|TRANSCODED, attempts, last_error)`
- `origin_rule(id, source: LEARNED|BUILTIN, pattern, origin_label, confidence)` and `cloud_item.inferred_origin` (+ user override).
- `picker_round(session_id, created_at, item_count, state)`
- Settings: destination root URI (persisted SAF permission), folder strategy, concurrency, Wi-Fi only, auto-disconnect.

**Key integrations**
- Google Photos Picker API: `sessions.create` → open `pickerUri` → poll `sessions.get` until `mediaItemsSet` → `mediaItems.list` (paginated) → downloads via `baseUrl` with `=d` (photo, EXIF minus GPS), `=dv` (video, transcoded; also the video half of motion photos). Base URLs expire after 60 minutes: the transfer engine refreshes them by re-listing the session before expiry, and sessions are deleted when no longer needed.
- OAuth: public Android client (no secret) created by the owner in a Google Cloud project; consent screen in Testing mode for personal use (note: refresh tokens expire after 7 days in Testing mode, which the UI must handle gracefully as "reconnect").
- Storage: `ACTION_OPEN_DOCUMENT_TREE` for the destination root with persisted URI permission; `DocumentsContract` for writes; `MediaScannerConnection`/`MediaStore` insert for visibility.

**Security requirements**
- Tokens encrypted with an AES key held in the Android Keystore (platform `javax.crypto`), stored in app-private storage; never logged; wiped on disconnect.
- TLS via the platform; no custom trust managers; cleartext traffic disabled in the manifest network security config.
- No `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`; least-privilege runtime permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, plus `READ_MEDIA_VISUAL_USER_SELECTED` handling on Android 14+).
- `allowBackup=false`; no exported components beyond the launcher activity and the OAuth redirect.
- Lint and Kotlin warnings are errors; R8 enabled for release.

# Development Roadmap

**Phase 0: Foundation (complete at setup)**
Gradle project with pinned, verified dependencies; Compose shell; CI-free local build via `./gradlew`.

**Phase 1: Device census and library view (usable without Google)**
- Runtime media permissions flow with rationale screens.
- MediaStore scanner for images/videos across volumes; incremental rescans; fingerprinting.
- Room database and repositories.
- Library screen grouped by folder and by month with counts and sizes; item detail screen.
- Onboarding: explanation screens, destination root picker (SAF), SD-card detection and highlight.
- Settings scaffold (DataStore).

**Phase 2: Connect and reconcile**
- OAuth PKCE flow with Custom Tabs, app-link redirect, Keystore-backed token store, token refresh, "Testing mode" expiry handling.
- Picker API client: session lifecycle, polling with backoff, paginated item listing, local cloud index, coverage tracking across rounds.
- Matching engine with confidence levels; sibling grouping; three-state assignment.
- Origin inference: learn folder↔filename patterns from the device census; built-in pattern table for common apps; per-item override.
- Library view extended with status filters, badges and evidence; connection status screen.

**Phase 3: Pull**
- Transfer job queue and WorkManager execution; bounded concurrency; Wi-Fi/battery constraints; pause/resume; retry with backoff; base-URL refresh.
- SAF writer with the `YYYY/YYYY-MM/<Origin>` layout; sibling download grouping; motion-photo video halves.
- Verification (size + hash) before DONE; media store registration; fidelity badges.
- Pull screen: scope selection, fidelity notice, progress, notification, completion summary.
- Manifest export (CSV + JSON) to the destination root; "Safe to clean up" summary.

**Phase 4: Leave and polish**
- Disconnect: revoke, wipe, guided confirmation checklist with deep links; auto-disconnect option.
- Cloud clean-up guidance screens.
- Backup-folder collision warning.
- Accessibility pass, dark mode, dynamic colour, empty/error states, first sideload-ready release build.

**Future (explicitly not v1)**
- Takeout-indexed full-fidelity mode: read `.supplemental-metadata.json` sidecars straight from a Takeout archive delivered to Drive/Dropbox/OneDrive/Box, giving originals, GPS, albums and original device folder names.
- Desktop app / browser extension that automates the Takeout download and integrates with the phone app.
- NAS destinations (SMB/WebDAV).
- iOS.
- Other providers (iCloud, OneDrive, Dropbox) and other data types.

# Logical Dependency Chain

1. Permissions + MediaStore scanner + database → **first visible screen: the device library** (everything else builds on this index).
2. Onboarding + destination picker (needed before any pull; cheap to build early).
3. OAuth + token store (nothing cloud-side works without it).
4. Picker client + cloud index → **second visible milestone: three-state library**.
5. Matching engine (depends on both indexes).
6. Transfer queue + downloader + SAF writer + verifier → **third milestone: first verified pull**.
7. Manifest + proof-of-copy (depends on verified jobs).
8. Disconnect/revoke/wipe (depends on token store and cloud index existing).
9. Guidance, polish, release build.

Each step is atomic and leaves the app in a shippable, testable state.

# Risks and Mitigations

- **Picker coverage is user-driven and capped at 2,000 items per session.** Mitigate with a clear rounds model, coverage counters, and "select by month" guidance; phase 2 Takeout-indexed mode removes the cap.
- **Video fidelity (transcoded) and GPS loss via Picker.** Mitigate with unmissable fidelity badges and the Takeout-indexed mode later; never describe a transcoded file as an original.
- **Google API changes without notice.** Mitigate by isolating the Picker client behind an interface, pinning API versions, and keeping research notes in `.taskmaster/docs/research-google-photos-export.md`.
- **Testing-mode refresh tokens expire after 7 days.** Mitigate with graceful reconnect prompts; this aligns with the "connect only while needed" philosophy.
- **Matching false positives/negatives.** Mitigate with confidence levels, evidence display, hash confirmation for ambiguous cases, and never auto-deleting anything.
- **Large libraries on low storage.** Mitigate with pre-flight free-space checks against the destination, streaming downloads, and no duplicate caching.
- **Pulled files re-uploaded by Google Photos backup.** Mitigate with the backup-folder warning and guidance.
- **Build toolchain on Apple Silicon (x86_64 SDK tools under Rosetta).** Working as of setup; fallback documented in the Dockerfile.
- **Dependency policy vs development speed.** Allowed set (AndroidX incl. Room, WorkManager, DataStore, Compose) covers the needs. JUnit 4 (with its hamcrest-core dependency) is the one approved third-party item, test-only, never in the APK.
- **Origin inference is a heuristic.** Mitigate by showing the inferred origin and letting the user override before pulling; unknown origins fall back to the month folder rather than guessing.

# Appendix

**Research summary (full notes with sources: `.taskmaster/docs/research-google-photos-export.md`)**
- Library API scopes removed 2025-03-31; third-party apps can only see app-created media.
- Picker API: ≤2,000 items/session, 60-minute base URLs, no GPS, `=dv` transcoded video, motion photo halves via `=d`/`=dv`.
- Data Portability API has no Photos scope; EEA/UK only.
- Takeout: no API; delivery to Drive/Dropbox/OneDrive/Box; sidecar JSON includes `googlePhotosOrigin.mobileUpload.deviceFolder.localFolderName` (used in the future Takeout-indexed mode to restore original folders).
- No Android/iOS API exposes Google backup status per item; it must be computed by reconciliation.

**Decisions settled with the owner (2026-10-20)**
- Folder layout: `<root>/Pomegranate/YYYY/YYYY-MM/<Origin>/` with inferred origin; unknown origin → `YYYY-MM/`.
- Hashing: fingerprint first; full SHA-256 only for ambiguous matches and for every pulled file.
- Tests: JUnit 4 approved as the sole third-party, test-only dependency.
- Application id / namespace: `com.gankdat.pomegranate` (owner's domain).
- Localisation: English only for v1.

**Open items**
1. **Google Cloud project.** Before phase 2 the owner creates a project, enables the Photos Picker API, and creates an Android OAuth client (package name + signing certificate SHA-1). Pomegranate documents the steps; nothing secret is stored in the repo.
</PRD>
