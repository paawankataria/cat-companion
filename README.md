# Cat Companion (Kotlin)

A small, draggable cat overlay for OnePlus and other Android phones. It keeps the original cat artwork supplied for this project and uses its poses for animation. The time and a tail-shaped battery meter are on by default. The cat looks concerned below 20%, does happy paw poses while charging, and sleeps after a minute without interaction. Tap for a short paw-and-cuddle animation; press and hold to open settings. Drag to move it.

The overlay animates at 30 frames per second with eased pose transitions, gentle sine-wave breathing, and smoothly interpolated battery fill. Pick a soft accent color in settings. The transparent launcher icon shows only the cat.

Android does not let ordinary apps replace the OxygenOS status-bar battery icon, so Cat Companion uses the **Display over other apps** permission to draw its floating cat. Weather is not included because it needs a weather provider and API key.

## Get the APK online

GitHub Actions builds the APK when changes are pushed to `main`:

1. Open the repository's **Actions** tab and choose the latest **Build Cat Companion APK** run.
2. Download the `cat-companion-debug-apk` artifact. GitHub packages workflow artifacts in a ZIP; inside is `CatCompanion.apk`.
3. Install the APK. Android may ask you to allow installation from the browser or file manager.

## Build locally

Requires JDK 17+, Gradle 8.9+, Android SDK Platform 35, and Build Tools 35.0.0. Run `gradle assembleDebug` from this folder. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## License

The source is available to view under the custom source-view-only terms in [LICENSE](LICENSE). Viewing is allowed; use, copying, modification, and redistribution require written permission. This is not an open-source license.
