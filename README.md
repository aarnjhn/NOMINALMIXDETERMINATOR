# Concrete Mix – Android app (WebView wrapper)

Your `index.html` is bundled in `app/src/main/assets/` and runs fully offline inside a WebView.

## Get the APK – Option A: no installs (GitHub)
1. Create a new GitHub repository and upload everything in this folder (including the hidden `.github` folder).
2. Open the repo -> **Actions** -> **Build APK** -> wait ~3-5 min (or click *Run workflow*).
3. Open the finished run -> **Artifacts** -> download `ConcreteMix-debug-apk`, unzip, copy `app-debug.apk` to your phone, tap to install (allow "install unknown apps" when asked).

## Option B: Android Studio
File -> Open -> this folder -> let Gradle sync -> Build -> Build APK(s).
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- Debug-signed APK: fine for personal use. For Play Store, create a release keystore and build a signed AAB.
- Export CSV opens Android's "Save as" picker (WebView can't download blob files by itself).
- Mix history lives in WebView localStorage; it survives app restarts and is cleared if you uninstall.
- Change the app name in `res/values/strings.xml`, the package id in `app/build.gradle`.
