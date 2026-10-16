# Cat Companion

A small Kotlin floating cat for Android. Its transparent cutout art is built from independent torso, head, ear, arm, foot, and tail layers. The cat breathes, blinks, shifts its ears and limbs, and sways its tail at about 30 frames per second. Tap for a happy cuddle animation; after a minute without interaction it curls into a slow breathing sleep. Drag to move it and press and hold to open settings.

The only widget information is the clock. Settings let you show or hide it and choose 12-hour or 24-hour time. The app uses Android's **Display over other apps** permission to place the cat above other apps.

## Get the APK online

GitHub Actions builds the APK when changes are pushed to `main`:

1. Open the repository's **Actions** tab and choose the latest **Build Cat Companion APK** run.
2. Download the `cat-companion-debug-apk` artifact. GitHub packages workflow artifacts in a ZIP; inside is `CatCompanion.apk`.
3. Install the APK. Android may ask you to allow installation from the browser or file manager.

## Build locally

Requires JDK 17+, Gradle 8.9+, Android SDK Platform 35, and Build Tools 35.0.0. Run `gradle assembleDebug` from this folder. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## License

The source is available to view under the custom source-view-only terms in [LICENSE](LICENSE). Viewing is allowed; use, copying, modification, and redistribution require written permission. This is not an open-source license.
