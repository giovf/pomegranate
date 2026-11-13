# Pomegranate privacy policy

_Last updated: 13 November 2026 · applies to the Android app `com.gankdat.pomegranate`_

Pomegranate is an Android app that shows where each of your photos and videos
lives (on your phone, in Google Photos, or both), copies the ones you pick
from Google Photos into a folder you choose, and then disconnects from Google.

**Short version: the developer never receives any of your data.** The app has
no server, no account system, no analytics, no advertising and no crash
reporting. Everything it knows stays on your phone until you remove it.

## Who is responsible

Pomegranate is published by an individual developer (giovf). Contact:
**[CONTACT ADDRESS: to be filled in before publishing]**.

Because nothing is sent to the developer, the developer holds no personal
data about you and cannot identify you, look anything up, or hand anything
over to anyone.

## What the app accesses, and why

| What | Why | Where it goes |
|---|---|---|
| Photos and videos on your phone, and their details (name, folder, size, dates, dimensions) | To build the list of what is on the phone and compare it with Google Photos | Stays in the app's private database on your phone |
| Your Google sign-in (an access token limited to the Google Photos Picker) | To let you pick items in Google Photos and download the ones you picked | Stored encrypted on your phone with a key held in the Android Keystore; sent only to Google |
| The items you pick in Google Photos, and their details | To show them, match them with files on the phone, and download them | Downloaded straight from Google to the folder you chose; details stay in the app's private database |
| The destination folder you choose | To save downloaded files there | A permission for that one folder, stored on your phone |
| Notifications | To show download progress while the app is in the background | Not sent anywhere |

The app does not read your contacts, location, messages, or anything else. It
does not read the location stored inside your photos for any purpose other
than leaving your files as they are.

## Network connections

The app connects only to Google, and only when you ask it to:

- `accounts.google.com` and `oauth2.googleapis.com` to sign in, refresh and
  revoke access;
- `photospicker.googleapis.com` and the download addresses it returns, to
  list and download the items you picked.

These connections are encrypted (HTTPS). Google handles them under
[Google's privacy policy](https://policies.google.com/privacy). No other
servers are contacted, including the developer's.

## Use of Google user data

Pomegranate asks for one Google permission: read-only access to the items you
select in the Google Photos picker
(`https://www.googleapis.com/auth/photospicker.mediaitems.readonly`). It
cannot see the rest of your library, and it cannot change or delete anything
in Google Photos.

Pomegranate's use of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements. Specifically:

- data from Google is used only to provide the features you see in the app
  (showing, matching and downloading the items you picked);
- it is never transferred to anyone, never used for advertising, and never
  used to train or improve any AI or machine-learning model;
- no person other than you can see it, because it never leaves your phone.

## Keeping and deleting data

- **Disconnect** (in the app) revokes the app's Google access, and deletes
  the sign-in token, the list of Google Photos items and your review
  decisions from the phone.
- **Uninstalling** the app, or clearing its storage in Android settings,
  deletes everything the app stored. The app is excluded from Android cloud
  backup and device-to-device transfer, so no copy is kept elsewhere.
- Files the app downloaded into your chosen folder are yours: they stay where
  they are until you delete them. The app never deletes files on your phone.
- You can also remove the app's access at any time at
  <https://myaccount.google.com/permissions>.

## Your rights

Under the UK GDPR and EU GDPR you have rights of access, correction, deletion
and objection. Since all processing happens on your own device and the
developer holds no data about you, you exercise them directly with the
controls above. If you have a question or complaint, use the contact address
above; you may also complain to your data protection authority (in the UK,
the ICO).

## Children

The app is not directed at children and does not knowingly process children's
data beyond what is described above for any user.

## Changes

If this policy changes, the new version is published at this address with a
new date. A change that would let any data leave your phone would also be
announced in the app before it takes effect.
