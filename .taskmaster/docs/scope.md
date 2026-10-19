# Pomegranate — raw project scope (as provided by the user, 2026-10-18)

The project/app is called Pomegranate. It is named after the story of Persephone and the pomegranate seeds Hades gives to Persephone. A concept of perilous hospitality — a trap that feels convenient, generous or irresistible at first but secretly revokes your autonomy, and you realise it only through the effort and friction required to escape once you are clairvoyant and attempt to leave. This is to mirror the current chokehold big cloud service providers have on the average consumer today.

Our initial primary focus will be to remove the friction in getting all your photos from Google Photos (cloud) to local storage, with future functionality enabling you to easily move this data to a NAS. We will then try to expand beyond photos and Google to other data types, but for now we will focus entirely on Google Photos. The user has been through the process themselves and knows what the app's functionality needs to be:

1. **Google Takeout** — unsure if anything can be done here; research whether there is any way of automating this data extraction process / any integration. Issues encountered:
   - Google divides files into parts of 2 GB each, leading to a very large list of items to download.
   - The page refreshes every time you download an item, so when downloading in quick succession it is easy to miss a download for a file you just clicked that hasn't started.
   - The names of the files to download do not match the title of the file downloaded, so if you miss one it is very hard to pinpoint which one.
   - Typical browsers are unreliable when downloading a large number of large files for people without gigabit internet. Leaving them overnight, you can wake up to cancelled downloads because all the large files were queued simultaneously. Workaround used: batch downloads via a download manager limited to 3 concurrent downloads.

2. The Google Photos mobile app does **not** show clearly which media is cloud-only, local-only, or local & cloud.

3. The Google Photos mobile app has no option to download all media in the cloud, including metadata and the multiple file formats that all point to one piece of media. Our app should support all media file formats.

4. From an average user's perspective it is hard to understand the connection between local folders and cloud location. Photos on local storage are stored in various folders (unclear whether image and metadata folders are the same), and in Google Photos they are also sorted into categories etc. — it is unclear whether in the cloud they are distributed into folders mirroring the phone's storage, or whether display location is simply metadata.

5. Tried an app called PhotoSync — quite good, but UI-wise it was unclear which photos are where, and whether downloading from cloud would place them into the local folder they belong to (app-specific folders etc.). Reference code for inspiration: https://github.com/morluto/rea

6. Didn't feel comfortable giving PhotoSync access to Google cloud data, especially as it remains connected after the operation is done. We probably can't avoid connecting the Google account and granting storage access, but we must guide the user on how to disconnect the app, and it should leave no trace once they are done — emphasis on security and privacy.

## Future (not v1)
- Move data easily to a NAS.
- Expand beyond photos and beyond Google to other data types/providers.
