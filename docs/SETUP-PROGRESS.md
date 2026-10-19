# Setup progress

Resume from the first unchecked step. Guide: `docs/SETUP.md`.

- [x] Step 0 — Sanity checks (git ok, node v22.23.3, task-master 0.43.1, gh 2.102.0)
- [x] Step 1 — Get the project scope (saved to `.taskmaster/docs/scope.md`, confirmed by user 2026-10-19)
- [x] Step 2 — Clarifying interview (2026-10-19)
- [x] Step 3 — Dev environment deltas (edits done 2026-10-19; **container rebuild required**, then verify: `java -version` = 17, `sdkmanager --list_installed` shows platform-tools / android-35 / build-tools 35.0.0, and `$ANDROID_HOME/build-tools/35.0.0/aapt2 version` runs under Rosetta)
- [ ] Step 4 — Scaffold the project (verified it runs)
- [ ] Step 5 — Write the PRD (user explicitly approved)
- [ ] Step 6 — Initialise Taskmaster
- [ ] Step 7 — Update always-loaded docs (CLAUDE.md, ARCHITECTURE.md, .env.example, README.md)
- [ ] Step 8 — Wrap up

## Decisions so far

- Project folder / repo name: `pomegranate`
- Raw scope saved to `.taskmaster/docs/scope.md` (2026-10-18). Awaiting user confirmation of the reflected-back summary.
- Research done (2026-10-18), recorded in `.taskmaster/docs/research-google-photos-export.md`. Headline: Library API can no longer read a user's library (since 2025-03-31); Data Portability API has no Photos scope; Takeout has no API but can deliver to Drive/Dropbox/OneDrive/Box, which is the recommended automation path. Per-item backup status has no OS API, must be computed by reconciling the export against the device media store.
- https://github.com/morluto/rea is a reverse-engineering toolkit; user suggested it (optionally) for inspecting PhotoSync. Declined: not reverse-engineering a closed-source commercial app.
- **Scope confirmed 2026-10-19**: v1 is a **mobile app** focused on scope points 2–5 (cloud vs local visibility, download cloud media with full metadata/formats, folder mapping, privacy/disconnect). Takeout automation (point 1) is a **later, optional** add-on (desktop app / browser extension integrating with the mobile app), never the entry point.
- "All media formats" = never drop an item for its type; keep sibling files together (RAW+JPEG, HEIC+MOV, original+edited, sidecars). No transcoding/viewing requirement.
- Hard constraint from research: no API lets a third-party app enumerate a user's Google Photos library (Library API closed 2025-03-31). The phone-side "cloud index" must come from the Picker API (user-selected, ≤2000/session, no GPS, transcoded video) or a Takeout archive (full fidelity, but needs a Takeout run + storage). **Decided 2026-10-19: Picker API first** for v1; Takeout-indexed full-fidelity mode is phase 2 (also powers the desktop/browser export add-on). UI must be explicit that Picker video is transcoded and GPS is stripped.
- **Platforms**: Android first, iOS later.
- **Stack**: **Native Kotlin (Android)**, user's explicit choice. Supersedes the template's "TypeScript everywhere" convention; CLAUDE.md to be rewritten at Step 7. Implies JDK + Android SDK in the dev container (Step 3) and build verification via Gradle (no emulator inside the container; user tests on a physical device / host Android Studio).
- **Backend**: none. On-device only; Google OAuth directly app↔Google. Needs a Google Cloud project for the OAuth client ID (user to create; Picker API scope `photospicker.mediaitems.readonly`).
- **Intent**: personal tool first (sideloaded APK on the user's own phone), Play Store later. Design OAuth so a later verification is straightforward but don't block on it.
- **Constraints**: GDPR / UK-GDPR audience; on-device-only processing documented. No formal accessibility target yet (keep sensible defaults). Current Android versions.
- **Destination storage**: users commonly have an SD card / removable storage; the app must let the user choose the destination root (including removable storage), via the system folder picker (SAF) rather than legacy all-files permission. Open design question for PRD: mirror original device folder names under the root by default?
- **Security / dependency policy (confirmed 2026-10-19)**: "never compromise user data" is the app's defining quality. Dependencies: Android SDK + Kotlin stdlib + Google AndroidX/Jetpack only; **no third-party libraries without asking the user**, each justified in writing; OAuth/TLS/crypto/token storage on platform implementations (Keystore, system TLS, Custom Tabs); all build deps pinned with Gradle dependency verification. On-device only, no analytics/telemetry, network only to Google APIs, tokens wiped on disconnect, guided revoke flow, verify-before-report for every written file. (User initially asked for "all code from scratch", then withdrew that in favour of this policy.)
- **Dev container (Step 3, 2026-10-19)**: host is Apple Silicon → arm64 container. Google's Android build-tools are x86_64-only; they run via Docker Desktop Rosetta (confirmed active) + Ubuntu multiarch amd64 libc/libstdc++/zlib. Dockerfile adds: openjdk-17-jdk-headless, amd64 runtime libs, Android cmdline-tools `15859902` (sha256 `4e4c464f…df583`, verified against developer.android.com), `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`, `ANDROID_HOME=/opt/android-sdk`. devcontainer.json: renamed, Kotlin/Gradle/XML extensions, ESLint/Prettier removed, no ports. `.claude/settings.json`: frontend-design plugin enabled. Fallback if Rosetta breaks: apt `aapt` (arm64) + `android.aapt2FromMavenOverride`.
- **Out of scope v1**: Takeout/desktop export add-on; NAS/network destinations; other providers and data types. Cloud deletion/modification is *impossible* via API (not a scope choice): v1 can only guide the user to free space in Google Photos after verifying local copies.
