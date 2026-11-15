# Preparing a Google Play release

Status: **preparation only** (owner go-ahead 2026-11-13). Nothing here has been
submitted. Every step that touches a Google account is done by the owner.
Requirements were checked against Google's pages on 2026-11-13; items marked
**(unconfirmed)** could not be confirmed from a primary source and must be
checked in the console when you get there.

## 0. Decisions (owner, 2026-11-15)

| # | Decision | Outcome |
|---|---|---|
| A | Where the privacy policy and homepage live | `https://gankdat.com/pomegranate/` and `https://gankdat.com/pomegranate/privacy/`. The pages are in `site/pomegranate/` (static, no scripts, no third-party requests): upload that folder to the web root as `pomegranate/`. |
| B | Public contact email | `info@gankdat.com` (in the policy, the homepage, and to be used on the store listing). |
| C | Store title | `Pomegranate for Google Photos` (29 of 30 characters; "X for Google Photos" is the form Google's Photos UX guidelines ask for). Fallback if Play objects: `Pomegranate: photo exit tool`. |
| D | 12 closed testers | Still open. Needed for 14 continuous days before production access; start recruiting early. |

The app links to the hosted policy from Settings → Privacy policy → "Open in
browser" (Play requires the policy inside the app too). The link is handed to
the phone's browser; the app makes no request of its own. The policy text
lives twice, in `docs/privacy-policy.md` and
`site/pomegranate/privacy/index.html`: change both together.

Reading the scope classification (order of work, step 1): the Data Access
page lists only scopes that have been added to the consent screen, so it is
empty until you click **Add or remove scopes**, paste
`https://www.googleapis.com/auth/photospicker.mediaitems.readonly` under
"Manually add scopes", then **Add to table**, **Update** and **Save**. The
scope then appears under non-sensitive, sensitive or restricted. Adding it
does not change how the app behaves while the consent screen is in Testing.

## 1. Risks to know before spending time

1. **Photos API policy.** Google's policy says "do not make a substitute for
   Google Photos" and allows broad access only "for user-initiated export
   transfers". Pomegranate is exactly a user-initiated export tool and uses
   only the Picker, but the listing and the OAuth submission must say so in
   those words. Never describe it as a gallery or a Google Photos replacement.
2. **OAuth verification.** The scope's classification is visible only in the
   Cloud Console (Data Access page) **(unconfirmed)**. If it is *sensitive*:
   homepage + policy on a verified domain and a demo video of the sign-in
   flow. If *restricted*: also a paid security assessment (CASA), which would
   make a public release impractical for a free personal app. Check this
   first; it decides whether the rest is worth doing.
3. **Unverified production is capped** at 100 users for the lifetime of the
   project, with a warning screen. Whether leaving Testing removes the 7-day
   sign-in expiry without verification is **unconfirmed**.
4. **Photo and video permission declaration.** Play allows
   `READ_MEDIA_IMAGES/VIDEO` only when broad access is the core function.
   Gallery apps are the named example; a whole-library census is a reasonable
   fit but not guaranteed **(unconfirmed)**. A rejection here would force a
   redesign around the system photo picker, which cannot do a census.
5. **Foreground service limit.** On Android 15+ `dataSync` services get 6
   hours per 24 hours (reset when the app comes to the foreground). The queue
   is persistent and rescheduled every 15 minutes, so a long pull should
   resume, but this has not been tested on a phone. Android 16 also counts
   long-running workers against job quota; "user-initiated data transfer"
   jobs are the exempt alternative if this bites.

## 2. Order of work

1. Cloud Console: read the scope classification (risk 2). Stop here if
   restricted.
2. Decisions A to D. Publish `docs/privacy-policy.md` and a one-page homepage.
3. Play Console account: US$25, identity check, verify an Android device with
   the Play Console app. Declare **non-trader** (free, no ads, non-commercial
   licence). Public profile shows your legal name, country and email.
4. Upload key (section 3), create the app, enrol in Play App Signing, upload
   the first `.aab` to **Internal testing**.
5. Add a second Android OAuth client for the **Play app-signing** SHA-1 (Play
   Console → Test and release → App integrity). Without it sign-in fails for
   every Play install. Keep the existing debug client.
6. Fill in App content (sections 4 to 7) and the store listing (section 8).
7. Closed test: 12+ testers, 14 days (section 9). In parallel, submit OAuth
   verification with the demo video.
8. Apply for production access from the Play Console dashboard.

## 3. Signing and the bundle

Play requires an Android App Bundle and Play App Signing. The key you create
is only the *upload* key; Google holds the app-signing key and the upload key
can be reset if lost.

```bash
mkdir -p keys
keytool -genkeypair -v -keystore keys/pomegranate-upload.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Put the four `pomegranate.signing.*` properties in `local.properties` exactly
as in `docs/RELEASE.md` section 2 (no build changes are needed; `keys/` and
`*.jks` are git-ignored), then:

```bash
./gradlew build bundleRelease
# app/build/outputs/bundle/release/app-release.aab
# app/build/outputs/mapping/release/mapping.txt  (upload with the bundle)
```

`bundleRelease` was verified to build on 2026-11-13 (unsigned, version 0.2.0,
code 2). Every upload needs a higher `versionCode`.

Note: a sideloaded APK signed with your own key and a Play install are signed
differently and cannot update each other.

## 4. Data safety form

- Does your app collect or share any of the required user data types? **No.**
  Play defines collection as data leaving the device; on-device processing is
  not collection, and a transfer the user starts themselves is not sharing.
  Treating the device-to-Google sign-in this way is our reading of the
  guidance, not something Google states for this exact case **(unconfirmed)**.
- The encryption and deletion questions only appear for declared data types.
- Privacy policy URL: the hosted copy of `docs/privacy-policy.md`.

## 5. Photo and video permissions declaration (draft)

> Pomegranate's core function is a census of the whole device library: it
> compares every photo and video on the phone with the items the user picks
> from Google Photos and labels each one "on this phone", "in Google Photos"
> or "both", so the user knows exactly what still needs to be copied before
> leaving Google Photos. This requires reading the metadata of all media on
> all storage volumes; a one-time photo picker cannot answer "which of my
> thousands of items are missing". Media is read on-device only and is never
> uploaded. If the user grants access to selected photos only, the app keeps
> working on that selection and says so on screen.

Video to record: first launch → permission explanation screen → grant →
library with the three states → the partial-access banner.

## 6. Foreground service declaration (draft)

- Type: `dataSync`; use case: **Network transfer: upload or download**.
- Description: "After the user selects items and taps Pull, the app downloads
  those photos and videos from Google Photos to the folder the user chose,
  verifies each file, and shows progress in a notification. Libraries are
  often tens of gigabytes, so the transfer must continue while the screen is
  off."
- Impact if deferred or interrupted: "Download links from Google expire after
  60 minutes, so a deferred transfer fails and the user must pick the items
  again; an interrupted transfer leaves the user's export incomplete."
- Video: choose items → Pull → notification with progress → leave the app →
  completion.

## 7. Other App content answers

- **App access**: sign-in is with the reviewer's own Google account. Provide a
  test Google account that has sample photos and is added as an OAuth test
  user, with step-by-step instructions (connect → pick → pull → disconnect).
- **Ads**: none. **Target audience**: 18+. **News / health / finance /
  government**: no.
- **Content rating**: questionnaire, utility app, no user-generated content
  shared with others.
- **Account deletion**: not applicable (the app creates no accounts); mention
  Disconnect.

## 8. Store listing (draft)

**Title**: `Pomegranate for Google Photos`

**Short description** (80 max):
`See where every photo lives, copy your Google Photos out, and verify each file.`

**Full description**:

> Pomegranate helps you move your photos and videos out of Google Photos and
> know, file by file, that the move worked.
>
> • See where everything lives. Every photo and video is marked as on this
>   phone, in Google Photos, or both.
> • Copy what is missing. Pick items from your Google Photos library and
>   Pomegranate downloads them to a folder you choose: internal storage, an SD
>   card or a USB drive.
> • Proof of every copy. Each downloaded file is verified and listed in a
>   manifest you can export.
> • Honest about quality. Google's picker removes location from photos and
>   re-encodes videos; every file is labelled with what you actually got.
> • Leave no trace. One tap disconnects from Google, revokes access and wipes
>   everything the app knew about your Google Photos library.
>
> Private by design: no account, no ads, no analytics, no servers. The app
> talks only to Google, and only when you ask it to.
>
> Pomegranate never deletes anything, on your phone or in Google Photos.
>
> Pomegranate is an independent app and is not affiliated with or endorsed by
> Google. Google Photos is a trademark of Google LLC.

Rules followed (Google Photos UX guidelines, Play impersonation policy):
always the full name "Google Photos"; "for Google Photos", never "Google
Photos X"; no Google logo, wordmark or recoloured icon in the app icon,
feature graphic or screenshots; the app shows the connected state and a clear
disconnect path.

Assets (drafts, 2026-11-15, in `docs/store/`):

- `icon-512.png`: 512×512, 32-bit PNG, same artwork as the app's adaptive
  launcher icon (`launcher-preview.png` shows it under a round and a
  rounded-square mask).
- `feature-1024x500.png`: 1024×500, 24-bit PNG, no text (Play shows the
  title beside it).
- `icon.svg` and `feature.svg` are the editable sources. The container has no
  SVG rasteriser or fonts, so the PNGs are drawn by
  `java docs/store/Render.java docs/store` from the same geometry; change the
  two together.

Still to make: at least 2 phone screenshots (use a test library, never real
photos).

## 9. Closed test plan

- Track: Closed testing, email list of **at least 12** testers (aim for 15 to
  absorb drop-outs); each must opt in and stay opted in for 14 consecutive
  days. Each tester's Google account must also be an OAuth test user while
  the consent screen is in Testing.
- Ask each tester to: scan the library, connect, pick 20 to 50 items including
  a video, pull to internal storage, open the proof screen, disconnect, and
  report phone model, Android version and anything confusing.
- Keep notes: Play asks how the test went and what changed when you apply for
  production access (review takes about a week).

## 10. Before the first upload

- [ ] Task 26 walkthrough passed on a real phone (nothing here replaces it).
- [ ] One long pull (over 6 hours or interrupted) resumes on Android 15+.
- [ ] Privacy policy hosted: upload `site/pomegranate/` and check both
      addresses load (contact filled in and Settings link done, 2026-11-15).
- [ ] Listing text and screenshots reviewed against section 8 rules.
