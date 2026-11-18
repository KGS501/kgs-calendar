# Publishing KGS Calendar in the Microsoft Store

The Store is the official way to get the Windows app. The `.msi` stays on GitHub releases for people
who want it. Both are the same app. The package is an MSIX, which the Store signs itself, so no
certificate is needed and Windows doesn't warn about it.

## First submission

### 1. The account and the name

This is done for KGS Calendar, and the identity is in
[`desktopApp/msix/store-identity.properties`](../../desktopApp/msix/store-identity.properties). For
reference, or to set it up again:

1. Sign up at [storedeveloper.microsoft.com](https://storedeveloper.microsoft.com/) as an individual
   developer, with a personal Microsoft account. It's free, and you can't change the account type
   later. Going through Partner Center directly can land you in the older sign-up, which may charge a
   fee.
2. In [Partner Center](https://partner.microsoft.com/dashboard), under **Apps and games**, choose
   **New product > MSIX or PWA app** and reserve the name **KGS Calendar**.
3. Open **Product identity** and copy the three values into the identity file:

   | Partner Center | In the file |
   | --- | --- |
   | Package/Identity/Name | `identityName` |
   | Package/Identity/Publisher | `publisher` |
   | Package/Properties/PublisherDisplayName | `publisherDisplayName` |

   The Store rejects a package whose identity doesn't match.

### 2. Build the package

Run the **Windows Store package** workflow on `main` (GitHub > Actions > Run workflow). It builds the
package, installs and starts it on a clean Windows, runs Microsoft's certification kit, and leaves
`store-package` and `certification-report` as downloads. Upload the `.msix` from `store-package`.

To build it locally instead, you need `makeappx.exe` and `makepri.exe` from the Windows SDK:

```powershell
$env:WINDOWS_SDK_BIN = '<folder with makeappx.exe>'   # not needed with an installed SDK
.\gradlew.bat :desktopApp:packageMsix
```

The package ends up in `desktopApp/build/msix`.

### 3. Fill in the submission

| Page | What to enter |
| --- | --- |
| Pricing and availability | Free; all markets, or the ones you choose |
| Properties | Category **Productivity**; privacy policy `https://kgs501.github.io/kgs-calendar/`; support `https://github.com/KGS501/kgs-calendar/issues` |
| Age ratings | Answer the questionnaire; see below |
| Packages | Upload the `.msix` |
| Store listings | [`listing.en.md`](listing.en.md), with the images in [`screenshots`](screenshots), [`art`](art) and [`logo`](logo). The package also offers German: remove that language here, so the English listing shows everywhere |
| Submission options | The `runFullTrust` note below |

**Age ratings.** The app has no violence, sex, gambling or similar content, no purchases, and no ads.
Users can't talk to each other in it, and it doesn't share their location. It does connect to the
internet, to the calendar servers the user adds. Say so if asked.

**The `runFullTrust` note.** Partner Center asks why the package needs this restricted capability:

> KGS Calendar is a desktop application built with Compose Multiplatform that runs on a bundled Java
> runtime. It needs full trust to run that runtime, to stay in the notification area so reminders
> appear while its window is closed, and to reach the CalDAV servers the user adds. It doesn't change
> system settings and collects no data.

### 4. Submit and check

Certification usually takes up to a few days. Once it's live, install the app from the Store on a
machine and check it opens. People moving from the `.msi` can install it next to that and then
uninstall the `.msi`: the Store app finds their data by itself.

## Store images

[`tools/generate_microsoft_store_images.py`](../../tools/generate_microsoft_store_images.py) draws the
screenshots and art from the app captures in [`app-captures`](app-captures), in the style of the Google Play
images, and needs Chrome or Edge. After a change to the app's look, retake the captures from a demo
profile and run it again:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dsun.java2d.uiScale=1.25 -Duser.language=en -Duser.country=GB'
& 'KGS Calendar.exe' --data-dir <empty folder> --demo
```

The captures use a 1500 by 830 window in light mode, with tasks coloured by priority and the tasks
panel pinned. The dark pair uses dark mode.

## Later updates

1. Raise `appVersionName` in `desktopApp/build.gradle.kts`, or `windowsRebuild` for a fixed build under
   the same version name. The Store only takes a higher version.
   The package's version comes from it, and its last number is always 0.
2. Run the workflow and upload the new `.msix` in a new submission.

Store users then get it automatically.

## Testing betas through the Store

Betas (versions like V.2.0.2-1) go to a **package flight** first, the Store's counterpart to Play's
internal testing and TestFlight: only the people in its group get the flight's package, through the
real Store install and its updates. Everyone else keeps the production package.

| | Name in Partner Center |
| --- | --- |
| Flight group (a known user group, under Customer groups) | **KGS internal testers** |
| Package flight (on the app's overview, under package flights) | **Internal testing** |

The group holds the Microsoft account the owner's laptop uses in the Store app. To add a tester, edit
the group under Apps and games > Customer groups; each tester must sign in to the Store with the account
listed there.

### A new beta

1. Raise the version as for any update (see above). **The flight's package must be higher than the
   production package**, or the testers' Store keeps production: V.2.0.2-1 is 2.0.201.0, above
   V.2.0.1's 2.0.100.0. Production isn't touched by any of this.
2. Run the **Windows Store package** workflow with **source_ref** set to the beta's tag and
   **publish** set to `flight`:

   ```sh
   gh workflow run windows-store.yml --ref main -f source_ref=V.2.0.2-1 -f publish=flight
   ```

   It builds and certifies the package as always, then uploads it to the **Internal testing** flight
   and submits it to certification. Nobody has to upload anything by hand.
3. Certification takes a few hours, at most three business days; the flight is then published to
   the group by itself. Partner Center shows the state under KGS Calendar > **Internal testing**.
   (A flight already in certification can't take a new package: wait for it, or cancel it there.)
4. On the laptop, signed in to the Store with the group's account, open KGS Calendar in the Store and
   check for updates: it installs the flight's version. Check **About** shows the beta's version.

### Promoting a tested beta

The owner decides when a beta is ready; nobody promotes it without them.

1. Run the workflow with **publish** set to `production` and **source_ref** set to the tested tag.
   The publish step waits in the `store-production` environment until the owner approves it on the
   run's page in GitHub; then it uploads the package as a production update and submits it. Change
   "What's new" in Partner Center first if it should say something new. Or, in Partner Center, open
   **Internal testing** and make its package available to everyone (Partner Center calls this
   creating a submission from the flight).
2. Or ship a final build of its own in a normal update submission (see above). Mind the numbers: a
   final V.2.0.2 maps to 2.0.200, *below* its betas (V.2.0.2-1 is 2.0.201), so it can't replace a
   published beta package. Either promote the tested beta's package as in step 1, or give the final
   build a `windowsRebuild` above the last beta's number (V.2.0.2 with `windowsRebuild = 2` is
   2.0.202).

After the production update is out, the flight's package is lower than production and the testers get
production again until the next beta.

### How the workflow reaches the Store

The publish job uses the Store submission API through the `msstore` CLI
(`microsoft/microsoft-store-apppublisher`). It signs in as the Microsoft Entra app **KGS Calendar
Store publishing** in the tenant **KGS501.onmicrosoft.com**, which is linked to the Partner Center
account and has the Manager role there. The flight's id is in the workflow (`STORE_FLIGHT_ID`).

Its values are **environment secrets**, not repository secrets: `AZURE_AD_TENANT_ID`, `SELLER_ID`,
`AZURE_AD_APPLICATION_CLIENT_ID` and `AZURE_AD_APPLICATION_SECRET`, set the same on both
environments, `store-flight` and `store-production` (Settings > Environments). Only the publish job
runs in one of them, so no other job or workflow can read the secrets. Both environments accept
**only the branch `main`**: dispatch with `--ref main` (pick what gets built with `source_ref`); a run
dispatched from any other branch can't reach the secrets, so a workflow changed on a branch can't
publish. `store-production` also waits for the owner's approval. Changing the environments or their
secrets needs admin rights on the repository.

The key comes from Partner Center > Account settings > User management > Microsoft Entra applications
> the app > **Add new key**; it is shown once. The current key expires on 2028-11-15 (the date is on
that page). Before then, add a new key, set `AZURE_AD_APPLICATION_SECRET` on both environments
(`gh secret set AZURE_AD_APPLICATION_SECRET --env store-flight`, then `--env store-production`), and
remove the old key on that page.

The API can't upload to a submission that was started in Partner Center, and it can't delete one
either. **Don't start a draft in Partner Center** (an update, or a new package for the flight) when the
workflow should publish; if one is there, the run stops with "Retrieved a submission that was created in
Partner Center". Delete that draft in Partner Center, then run again. Deleting the only submission of a
flight that was never published deletes the flight too: create it again with the API (POST
`applications/9P0NZM3HSQ92/flights` with the name and the group's id) and put its new id in the
workflow. The flight in the workflow was created that way.

## How the Store app differs

| | Store app | `.msi` |
| --- | --- | --- |
| Program folder | Read-only, in `WindowsApps` | `%LOCALAPPDATA%\KGS Calendar` |
| Updates | The Store | A newer installer; `UpdateWatcher` closes the app for it |
| Data | The package's folder, deleted when uninstalled | `%LOCALAPPDATA%\KGS501\KGS Calendar`, kept |
| Open at sign-in | Startup task in the manifest (`WindowsStoreLoginStart`) | Registry `Run` entry (`WindowsLoginStart`) |
| Notifications | Identity from the package | Announced in the user's registry (`WindowsNotifications`) |
| Class archive for faster starts | None, as the folder is read-only | Built on the user's computer |

If `%LOCALAPPDATA%\KGS501\KGS Calendar` already exists, for example from the `.msi`, the Store app
uses that folder directly, so its data isn't deleted with the app. The privacy screen and the policy
say so.

## Trying a package on your own computer

A package needs a signature to install, and Windows has to trust it for the whole machine, which
takes administrator rights once:

```powershell
.\gradlew.bat -PmsixTest :desktopApp:packageMsix

$cert = New-SelfSignedCertificate -Type Custom -Subject 'CN=KGS501 Test' -KeyUsage DigitalSignature `
  -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddDays(14) `
  -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3', '2.5.29.19={text}')
signtool sign /fd SHA256 /sha1 $cert.Thumbprint /s My desktopApp\build\msix\KGS-Calendar-*-test.msix

Export-Certificate -Cert $cert -FilePath test.cer
# As administrator:
Import-Certificate -FilePath test.cer -CertStoreLocation Cert:\LocalMachine\TrustedPeople

Add-AppxPackage -Path (Get-Item desktopApp\build\msix\KGS-Calendar-*-test.msix)
```

Remove the certificate from both stores when you're done. An unsigned package doesn't work:
Windows refuses one that contains an ordinary app.
