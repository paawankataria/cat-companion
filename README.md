# Cat Battery (Kotlin)

A small Android overlay that displays a cat and the current battery percentage. A regular app cannot replace the OnePlus/OxygenOS status bar battery icon, so this shows a movable cat pill just below it instead.

## Get the APK online

This repository includes a GitHub Actions workflow. After the project is pushed to GitHub:

1. Open the repository's **Actions** tab.
2. Select **Build Cat Battery APK** and choose **Run workflow** (pushes to `main` also build automatically).
3. Open the completed run and download the `cat-battery-debug-apk` artifact.
4. Extract the ZIP; it contains `app-debug.apk`.

The APK is a debug build, suitable for personal installation. Android may ask you to allow installation from the browser or file manager used to open it.

## Use

Install and open the app, tap **Enable cat battery**, then allow **Display over other apps**. Drag the cat to move it; tap it to dismiss it. Open the app again to show it. If Android stops the overlay, reopen the app to start it again.

## Build locally

Requires JDK 17+, Gradle 8.9+, Android SDK Platform 35, and Build Tools 35.0.0. Run `gradle assembleDebug` from this folder. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## License

The source is available to view under the custom source-view-only terms in [LICENSE](LICENSE). Viewing is allowed; use, copying, modification, and redistribution require written permission. This is not an open-source license.
