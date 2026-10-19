# Releasing and sideloading Pomegranate

Pomegranate is distributed as a sideloaded APK. There is no store listing and
no server; a release is a signed APK plus the Google Cloud OAuth client that
the signing certificate is bound to.

## 1. One-time: Google Cloud project

1. Create a project at https://console.cloud.google.com/.
2. **APIs & Services → Library**: enable **Google Photos Picker API**.
3. **APIs & Services → OAuth consent screen**: External, add yourself as a
   test user. Leave the app in *Testing*: that is enough for personal use,
   with one consequence: refresh tokens expire after 7 days and the app asks
   you to sign in again (it tells you when).
4. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.gankdat.pomegranate`
   - SHA-1: of the certificate that signs the APK you will install (debug or
     release, see below). One client per certificate.
5. Put the client id in `local.properties`:
   `pomegranate.oauthClientId=1234567890-abc.apps.googleusercontent.com`
   and rebuild. The Connection screen says "no client id" until you do.

Certificate SHA-1 fingerprints:

```bash
# debug (the container's auto-generated debug key)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
# release
keytool -list -v -keystore keys/pomegranate-release.jks -alias pomegranate | grep SHA1
```

## 2. One-time: release keystore

```bash
mkdir -p keys
keytool -genkeypair -v \
  -keystore keys/pomegranate-release.jks -storetype PKCS12 \
  -alias pomegranate -keyalg RSA -keysize 4096 -validity 10000
```

Keep `keys/` out of git (`*.jks` is ignored) and back the file up somewhere
safe: losing it means future builds cannot update the installed app. Then add
to `local.properties`:

```properties
pomegranate.signing.storeFile=keys/pomegranate-release.jks
pomegranate.signing.storePassword=...
pomegranate.signing.keyAlias=pomegranate
pomegranate.signing.keyPassword=...
```

Without these four properties `assembleRelease` still works and produces
`app-release-unsigned.apk`, which Android will refuse to install.

## 3. Release checklist

1. Bump `versionCode` (integer, always increasing) and `versionName`
   (semantic) in `app/build.gradle.kts`.
2. `./gradlew build` — unit tests, lint (warnings are errors), debug and
   minified release.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (signed) and `app/build/outputs/mapping/release/mapping.txt` (keep it with
   the APK to read stack traces from that build).
4. Verify the signature and the version:
   ```bash
   $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   $ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -1
   ```
5. Install on a phone that has never had the app (fresh state) and walk
   through: onboarding → permission → library scan → connect → one picker
   round → pull a handful of items → proof of copy → export manifest →
   disconnect → check https://myaccount.google.com/permissions.
6. Tag: `git tag -a v<versionName> -m "Pomegranate <versionName>"`, and
   archive the APK and `mapping.txt` under that name.

## 4. Sideloading

On the phone:

1. **Settings → Apps → Special app access → Install unknown apps** (wording
   varies by maker): allow the app you will open the APK with, usually
   *Files* or your browser.
2. Copy `app-release.apk` to the phone (USB, Nearby Share, a cloud drive you
   trust) and open it from Files → Install.
3. Updates: install the new APK over the old one. It must be signed with the
   same key; a different key is refused and you would have to uninstall
   (which deletes the local index, not your pulled files).

From a computer with `adb` and USB debugging enabled on the phone:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

To check an APK before installing it, compare its signing certificate with
the one you created:

```bash
apksigner verify --print-certs app-release.apk | grep SHA-256
keytool -list -v -keystore keys/pomegranate-release.jks -alias pomegranate | grep SHA256
```

## 5. What R8 does and does not touch

Release builds are minified and resource-shrunk. No project keep rules are
needed (see `app/proguard-rules.pro` for why). If a release build crashes
with a `ClassNotFoundException` or a renamed class in a stack trace, retrace
it with `mapping.txt` and add a targeted rule with a comment.
