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
| Store listings | [`listing.en.md`](listing.en.md) and [`listing.de.md`](listing.de.md), with the images in [`screenshots`](screenshots) and [`logo`](logo) |
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

## Later updates

1. Raise `appVersionName` in `desktopApp/build.gradle.kts`. The Store only takes a higher version.
   The package's version comes from it, and its last number is always 0.
2. Run the workflow and upload the new `.msix` in a new submission.

Store users then get it automatically.

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
