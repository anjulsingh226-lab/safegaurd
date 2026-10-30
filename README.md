# PeekGuard

## Build APK on GitHub
1. Upload the contents of this folder to your GitHub repository root.
2. Open **Actions** and run **Build APK**, or push to `main`/`master`.
3. Download the `PeekGuard-apk` artifact from the completed workflow.

## Permission setup update
On first launch the app requests Camera and Notifications together where Android supports that. It then guides the user through Usage Access and Display over other apps. Android requires those special permissions to be approved in Settings; apps cannot grant them silently or combine them into one system dialog. Battery Unrestricted is optional and is no longer a required permission.

## Limitations
Android versions and phone manufacturers can restrict camera access while another app is in the foreground. Test on the target phone. The face detector counts visible faces; it does not identify who is the phone owner.
