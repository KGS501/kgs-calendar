# Publishing KGS Calendar in the App Store

`tools/ios/app_store_release.py` does everything that App Store Connect's API allows. It needs no
sign-in, so an update needs nobody to hand over a key.

## The private config

The tool reads `~/.config/kgs-calendar/app-store-config.json` (mode 600, never in this repo):

```json
{
  "key_file": "/home/you/.config/kgs-calendar/AuthKey_XXXXXXXXXX.p8",
  "key_id": "XXXXXXXXXX",
  "issuer_id": "…",
  "app_id": "…",
  "review_contact": {
    "contactFirstName": "…", "contactLastName": "…", "contactPhone": "+49…", "contactEmail": "…"
  }
}
```

The key is the same one as the `ASC_PRIVATE_KEY` secret of the `testflight` GitHub environment. The
review contact is only shown to App Review, and it goes here because this repo is public.

## Shipping an update

1. Raise `MARKETING_VERSION` in `iosApp/KgsCalendar.xcodeproj/project.pbxproj` and merge it.
2. Run the TestFlight workflow on `main`: `gh workflow run testflight.yml --ref main`. Its title gives
   the build number, for example `TestFlight build 7.1`.
3. Test that build, then:

   ```sh
   python3 tools/ios/app_store_release.py push --version 2.0.1 --whats-new "What changed"
   python3 tools/ios/app_store_release.py attach-build --version 2.0.1 --build 7.1
   python3 tools/ios/app_store_release.py status
   python3 tools/ios/app_store_release.py submit --version 2.0.1 --yes
   ```

`push` creates the version and fills it from [`listing.en.json`](listing.en.json). Screenshots carry
over from the previous version; pass `--screenshots <folder>` with `iphone/` and `ipad/` subfolders to
replace them, in file name order. `--release-type MANUAL` holds an approved version until someone
releases it, and `AFTER_APPROVAL` releases it by itself. Without the option the version keeps what it
has, and Apple copies that from the version before.

## What only the website can do

Apple's API has no public route for these, so they stay in
[App Store Connect](https://appstoreconnect.apple.com):

- **App Privacy.** Answered once, and only touched again if the app starts handling data differently.
  The answer is *Data Not Collected*: nothing reaches the developer, and location searches go to
  OpenStreetMap only to answer the request. Keep it in step with `index.html`.
- **Agreements, tax and banking**, and the EU trader status, under Business.
