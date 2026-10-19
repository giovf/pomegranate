# Pomegranate

An Android app for leaving Google Photos on your own terms.

Pomegranate shows you exactly where every photo and video lives (**on this
phone, in Google Photos, or both**), pulls everything that is cloud-only into
storage you choose (internal, SD card, USB), proves each copy with a verified
manifest, and then disconnects from Google leaving no trace.

It is named after the pomegranate seeds Hades gives Persephone: hospitality
that is generous on the way in and quietly revokes your autonomy, which you
only discover in the effort it takes to leave. Consumer cloud storage works the
same way.

## Status

Early development. v1 scope, phasing and architecture: `.taskmaster/docs/prd.md`
and `docs/ARCHITECTURE.md`. Research on what Google's APIs do and don't allow
(and why the app is designed the way it is): `.taskmaster/docs/research-google-photos-export.md`.

## Principles

- **Never compromise user data.** Runs entirely on the device. No servers, no
  analytics, no crash reporting. The only network traffic is to Google's own
  OAuth and Photos Picker endpoints.
- **Connect only while needed.** One tap revokes the Google token, wipes every
  local trace, and walks you through confirming it in your Google Account.
- **Never destructive.** The app never deletes device files and cannot delete
  from Google Photos (no API exists); it guides the manual clean-up only after
  copies are verified.
- **Honest about fidelity.** Google's Picker strips GPS from photos and
  transcodes video; every pulled file carries a badge saying what you got.
- **Minimal dependencies.** Android platform, Kotlin and AndroidX only; JUnit
  for tests. Every artifact is pinned and checksum-verified.

## Build and run

Requirements: JDK 17, Android SDK (platform 37, build-tools 36). The dev
container provides both.

```bash
./gradlew assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint (warnings are errors)
./gradlew assembleRelease        # minified release build (unsigned)
```

Install on a phone with developer mode enabled: copy the APK over, or from a
machine that can see the device run `adb install app/build/outputs/apk/debug/app-debug.apk`.
There is no emulator in the container.

Connecting a Google account needs an OAuth client id from your own Google
Cloud project (Photos Picker API enabled, Android client bound to the package
`com.gankdat.pomegranate` and your signing certificate). Put it in
`local.properties` as described in `.env.example`; nothing of the sort is
committed.

## Security model

- Everything is processed on the phone. The only hosts ever contacted are
  Google's: `accounts.google.com` and `oauth2.googleapis.com` for sign-in,
  `photospicker.googleapis.com` for the Picker API, and the signed media URLs
  Google returns for the items you picked.
- All traffic is HTTPS through the platform's `HttpsURLConnection` with the
  system trust store only: `res/xml/network_security_config.xml` forbids
  cleartext and user-added CAs, and there are no custom trust managers,
  hostname verifiers or certificate pins in the code.
- OAuth uses PKCE with a public Android client (no secret). Tokens live in one
  file encrypted with an AES-256-GCM key held in the Android Keystore; they are
  never logged and are wiped, key included, on Disconnect.
- Pulled files are written through the Storage Access Framework into the
  folder you chose, re-read and hashed (SHA-256) before they count as saved,
  and the app never deletes anything it did not just write and fail to verify.

## Development

- **Dev container**: open the folder in VS Code → "Reopen in Container". The
  image is arm64 (Apple Silicon) with Google's x86_64 SDK tools running under
  Docker Desktop's Rosetta emulation; see `.devcontainer/Dockerfile`.
- **Claude Code + Taskmaster**: work is broken down in `.taskmaster/tasks/`;
  `task-master next` shows the next task, `task-master set-status --id <id> --status done`
  closes it. Conventions for contributors (human or AI) are in `CLAUDE.md`.
- **Dependencies**: change versions only in `gradle/libs.versions.toml`, then
  regenerate checksums with `./gradlew --write-verification-metadata sha256 build`.

## Licence

Not yet decided.
