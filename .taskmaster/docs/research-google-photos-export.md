# Research: automating a full Google Photos export (as of 2026-10-18)

Background research for Pomegranate v1. Sources are linked inline; re-verify
before relying on any of this, Google changes these surfaces without notice.

## 1. Google Photos Library API / Picker API

- **Library API**: on 2025-03-31 the `photoslibrary`, `photoslibrary.readonly`
  and `photoslibrary.sharing` scopes were removed. `mediaItems.list/search/get`
  now return **only app-created content**; old scopes get `403`. No further
  changes in the release notes as of 2025-08-28. A third-party app **cannot
  enumerate or download a user's library** any more.
  - https://developers.google.com/photos/support/updates
  - https://developers.google.com/photos/support/release-notes
  - https://developers.googleblog.com/en/google-photos-picker-api-launch-and-library-api-updates/
- **Picker API**: the user selects items inside Google's own UI.
  `maxItemCount` defaults to and is capped at **2000 per session**; `baseUrl`s
  are valid for **60 minutes**. Metadata: createTime, filename, mimeType,
  dimensions, camera make/model, exposure settings, video fps. **No GPS.**
  `=d` downloads keep EXIF *except location*; `=dv` returns a **transcoded**
  video, not the original. Motion photos: `=d` still, `=dv` video part.
  Original vs edited behaviour undocumented (issue tracker: not byte-identical).
  - https://developers.google.com/photos/picker/reference/rest/v1/sessions
  - https://developers.google.com/photos/picker/reference/rest/v1/mediaItems
  - https://developers.google.com/photos/picker/guides/media-items
  - https://issuetracker.google.com/issues/110343547
- rclone's docs confirm the same limits; rclone's shared client_id is retired
  during 2026. https://rclone.org/googlephotos

## 2. Google Data Portability API: does NOT cover Photos

- Scopes cover Chrome, Maps, My Activity, Play, Search, Shopping, YouTube,
  Fitbit, Street View… There is **no `dataportability.photos.*` scope**.
  https://developers.google.com/data-portability/user-guide/scopes
- Availability: 27 EEA countries + Switzerland + UK only; not for under-18,
  Workspace-managed or Advanced Protection accounts.
  https://support.google.com/accounts/answer/14452558
- Mechanics (for reference if Photos is ever added): one-time or time-based
  30/180-day access, exports every 24 h, `InitiatePortabilityArchive` → poll →
  signed GCS URLs valid 6 h, archive kept 14 days; consent auto-reset 14 days
  after first initiate. All scopes are **restricted** → OAuth verification +
  annual CASA security assessment.
  - https://developers.google.com/data-portability/user-guide/time-based
  - https://developers.google.com/data-portability/user-guide/methods
  - https://support.google.com/cloud/answer/13464325
- Conclusion: not viable for Photos today. Monitor the release notes.

## 3. Google Takeout

- **No official API.**
- Delivery options: emailed link, **Google Drive, Dropbox, OneDrive, Box**;
  plus partner transfer to Flickr / SmugMug / OneDrive / iCloud Photos.
- Frequency: one-time, or **every 2 months for 1 year** (Photos-only exports
  are incremental: new and updated items).
- Formats: .zip / .tgz; split sizes **1 / 2 / 4 / 10 / 50 GB**.
- Download links expire after ~7 days; **5 downloads per archive**; password
  (+2SV) re-prompt on download; builds take minutes to days.
  https://support.google.com/accounts/answer/3024190
- Automation tools (all scrape the web session; ToS grey area; break on
  DOM/internal-API changes; need interactive re-auth):
  - **gxodus** (Go/Docker): one-time noVNC login, encrypted cookies, drives
    Takeout's internal `batchexecute` API to create/poll exports; downloads
    need chromedp + a fresh `rapt` re-auth token. https://github.com/thinkjk/gxodus
  - gphotos-cdp (Chrome DevTools scraper, unmaintained since ~2020).
    https://github.com/perkeep/gphotos-cdp
  - gphotosdl (rclone's headless-browser proxy; fragile).
    https://pkg.go.dev/github.com/rclone/gphotosdl
  - curl-with-cookies scripts. https://blog.omgmog.net/post/downloading-google-takeout-to-a-nas/

## 4. Takeout photo output quirks

- Layout: `Photos from YYYY/` folders plus one folder per album; media is
  **duplicated** into every album folder; some album items never appear in
  year folders.
- Sidecars: `<file>.supplemental-metadata.json` (since ~2024). Name part
  truncated to **47 chars**; duplicate naming mismatch (`IMG(1).jpg` vs
  `IMG.jpg(1).json`); JSON and media can land in **different zip parts**.
  - https://github.com/andyjohnson0/TakeoutExtractor
  - https://exiftool.org/forum/index.php?msg=80113
- Edited photos: original and `-edited` copy both exported (suffix may be
  truncated to `-edit`; edited may exist without original).
- Motion/live photos: Pixel `.MP`/`.MV` sidecars; iOS Live Photos as
  HEIC + MP4 pairs.
- **Device folder is recoverable**: not as a path, but the sidecar JSON has
  `googlePhotosOrigin.mobileUpload.deviceFolder.localFolderName` and
  `deviceType` (ANDROID_PHONE / IOS_PHONE), e.g. "Telegram Images".
  - https://github.com/TheLastGimbus/GooglePhotosTakeoutHelper/issues/395
  - https://pkg.go.dev/github.com/opdude/google-photo-exporter-metadata-fixer/pkg/google-export-json
- Existing fixers: **GPTH Neo** v5.x (album strategies, hash dedupe, GPS/date
  write-back) https://github.com/Xentraxx/GooglePhotosTakeoutHelper_Neo ;
  **immich-go** (Takeout → Immich) https://github.com/simulot/immich-go

## 5. Per-item backup status on Android / iOS

- **Android**: no public API exposes Google Photos backup state. The only
  system hook is `CloudMediaProviderContract.MediaColumns.MEDIA_STORE_URI`
  (cloud-media providers are an OEM-nominated pilot, reachable only via the
  system picker).
  - https://developer.android.com/guide/topics/providers/cloud-media-provider
- **iOS**: `PHImageResultIsInCloudKey` only covers iCloud, nothing about
  Google. https://developer.apple.com/documentation/photos/phimageresultisincloudkey
- PhotoSync only tracks what *it* has transferred; no Google backup detection.
  https://www.photosync-app.com/help/google-photos
- Implication: "cloud-only / local-only / both" has to be **computed by us**,
  by reconciling the Takeout export (incl. `localFolderName`) against a scan
  of the device's media store, matching on filename + timestamp + hash.

## Recommended approaches, ranked

1. **Takeout + cloud delivery (sanctioned, most robust).** Guide the user
   through a Photos-only Takeout delivered to Drive / Dropbox / OneDrive / Box
   (50 GB parts). Pomegranate pulls the archives through that provider's
   official API (no Takeout re-auth, no 5-download limit, resumable), then
   normalises: merge JSON sidecars, dedupe album copies, keep edited/original
   and live-photo pairs together, recover device folder.
2. **Takeout + assisted download.** In-app browser/WebView where the user logs
   in and the app drives the download queue (gxodus pattern). Works but
   fragile, needs interactive re-auth, ToS grey.
3. **Picker API supplement.** Official, but ≤2000 items/session, no GPS,
   transcoded video. Only suitable for small incremental top-ups.
4. **Data Portability API.** Not viable (no Photos scope, EEA/UK-only, CASA).
5. **Web scraping (gphotos-cdp / gphotosdl).** Avoid for a product.
