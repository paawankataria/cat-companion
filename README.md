# Cat Companion (Kotlin)

A small, draggable cat overlay for OnePlus and other Android phones. Its layered gray-and-cream art with amber eyes is based on the supplied cat illustration. The torso, head expressions, front paws, and tail move independently. The time and a tail-shaped battery meter are on by default. The cat looks sad below 20%, does happy paw poses while charging, and sleeps after a minute without interaction. Tap for a short paw-and-cuddle animation; press and hold to open settings. Drag to move it.

The overlay animates at 30 frames per second with eased head expressions, sine-wave breathing, a soft paw lift, a swishing tail, and smoothly interpolated battery fill. Pick a soft accent color in settings. The transparent launcher icon shows only the assembled cat.

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
