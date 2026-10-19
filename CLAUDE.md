# Project guidelines for Claude Code

This file is loaded automatically by Claude Code at the start of every session
(both `claude` in the terminal and the IDE extension). Keep it small and
high-signal — it's part of every prompt, so verbosity costs tokens.

## Architecture
- Product/system architecture lives in `docs/ARCHITECTURE.md` (imported
  below). That file tracks the PRD (`.taskmaster/docs/prd.md`); this file is
  conventions and practices only — keep PRD-specific detail out of here.

## Stack
- **Native Android app**, Kotlin 2.4, Jetpack Compose + Material 3. Single
  module `:app`, package `com.gankdat.pomegranate`.
- **Build**: Gradle 9.8 wrapper (`./gradlew`), AGP 9.4 (built-in Kotlin, new
  DSL), JDK 17. minSdk 30, target/compileSdk 37. No Node/npm in this project;
  `node_modules/` exists only for Taskmaster tooling.
- **Versions** live in `gradle/libs.versions.toml` only. Every artifact is
  checksum-pinned in `gradle/verification-metadata.xml`; after changing any
  dependency run `./gradlew --write-verification-metadata sha256 build` and
  commit the regenerated file.
- **Commands**: `./gradlew assembleDebug` (APK at
  `app/build/outputs/apk/debug/`), `./gradlew testDebugUnitTest`,
  `./gradlew lintDebug`, `./gradlew assembleRelease`. No emulator in the
  container: test on a phone via sideloaded APK or `adb` over Wi-Fi.
- Container is arm64; Google's x86_64 SDK tools run under Rosetta (see
  `.devcontainer/Dockerfile` for the fallback if that breaks).

## Conventions
- **Dependency policy (hard rule)**: Android platform, Kotlin stdlib and
  Google AndroidX/Jetpack only. JUnit 4 is the single approved third-party,
  test-only dependency. Anything else: ask first, justify in writing, pin it.
  Repositories are limited to Google and Maven Central.
- **User data is never compromised**: on-device only, no analytics/telemetry,
  network calls only to Google's OAuth and Photos Picker endpoints, secrets
  behind the Android Keystore, nothing logged that identifies the user or
  their media, every written file verified before it is reported as saved.
  The app never deletes device files.
- Platform implementations for OAuth/TLS/crypto/HTTP (`HttpsURLConnection`,
  Custom Tabs, `javax.crypto` + Keystore); no hand-rolled security code.
- Kotlin and lint warnings are errors (`allWarningsAsErrors`,
  `warningsAsErrors`); fix the cause, don't suppress without a comment.
- Feature-oriented packages: `census.device`, `census.cloud`, `reconcile`,
  `transfer`, `trust`, `ui`, `data`. ViewModels expose immutable UI state;
  Room for persistence, DataStore for settings, WorkManager for background.
- Explicit visibility and return types on public API; `internal` by default
  across package boundaries; no `!!` outside tests.
- UI: Material 3 with dynamic colour and dark mode; every control has a
  content description; the three media states (cloud-only / device-only /
  both) always use the same colour + icon pairing.
- Storage: Storage Access Framework for the user-chosen destination root; no
  `MANAGE_EXTERNAL_STORAGE`, no legacy storage permissions.

## Workflow
- Use Taskmaster for breaking down work. Before implementing any non-trivial
  feature, check `task-master next` or read `.taskmaster/tasks/tasks.json`.
- Update task status with `task-master set-status --id <id> --status done` as
  you complete each one.
- Don't commit `node_modules/`, `.taskmaster/reports/`, `local.properties`,
  signing keys, or anything in `.gitignore`.
- Research notes on Google's APIs and their limits are in
  `.taskmaster/docs/research-google-photos-export.md`; re-verify before
  relying on them.

## Things to ask before doing
- Adding a new top-level dependency (see dependency policy)
- Modifying anything inside `.devcontainer/`
- Deploying, publishing, or submitting to any store/registry
- Adding any permission to `AndroidManifest.xml`
- Adding any network endpoint other than Google's OAuth / Picker APIs
- Changing the OAuth scopes, the application id, or the signing configuration
- Anything that deletes or modifies user media or the cloud index outside
  the explicit Disconnect flow

## Product architecture
**Import the architecture overview; treat as if inlined here.**
@./docs/ARCHITECTURE.md

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md
