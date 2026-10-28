# Bold

Launcher with a permanent QWERTY keyboard on the bottom 40% and apps forced into the top 60%.

## Build
Open in Android Studio (it generates the Gradle wrapper), Build > Build APK, or `./gradlew assembleDebug`.

## Install
1. Remove all accounts from the phone (or factory reset).
2. `./setup.sh`
3. Press Home and choose Bold if prompted.

## Edit before you build
`Config.kt`: HIDDEN apps, optional FAVORITES, and the KIOSK / LOCK_DOWN flags. The app list is discovered automatically.
Leave KIOSK and LOCK_DOWN false until the layout works, then flip KIOSK on, then LOCK_DOWN.

## Escape hatch
Long-press the clock in the launcher header: stops lock task and clears device owner.
Debug builds installed with `-t` are test-only, so `adb shell dpm remove-active-admin com.bold.launcher/.AdminReceiver` also works.
