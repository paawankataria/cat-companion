# Cat Companion (Kotlin)

A cute, movable floating cat for OnePlus and other Android phones. It can show the local time, battery percentage, Android's current battery status, and time zone. Choose a soft lilac, blush, mint, sky, or peach tint and adjust its strength. The cat artwork and launcher icon use the cat images supplied for this project.

A regular app cannot replace the OxygenOS status bar battery icon, so Cat Companion floats above other apps instead. Weather is not included because it needs a weather provider and API key.

## Get the APK online

The GitHub Actions workflow builds the APK when changes are pushed to `main`:

1. Open the repository's **Actions** tab and select the latest **Build Cat Battery APK** run.
2. Download the `cat-battery-debug-apk` artifact. GitHub packages workflow artifacts as ZIP files; inside is `app-debug.apk`.
3. Install the APK. Android may ask you to allow installation from the browser or file manager used to open it.

## Use

Open Cat Companion, choose the display options and tint, then tap **Show floating cat**. Allow **Display over other apps** when Android asks. Drag the cat to move it; tap it to reopen settings. Use the same button to hide or show the overlay.

Battery status is the live status Android exposes (for example, Good or Overheating). Android does not provide a consistent long-term battery-health percentage to ordinary apps, so the app does not present one as a health score.

## Build locally

Requires JDK 17+, Gradle 8.9+, Android SDK Platform 35, and Build Tools 35.0.0. Run `gradle assembleDebug` from this folder. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## License

The source is available to view under the custom source-view-only terms in [LICENSE](LICENSE). Viewing is allowed; use, copying, modification, and redistribution require written permission. This is not an open-source license.
