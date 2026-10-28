#!/usr/bin/env bash
# Run with the phone plugged in, USB debugging on, and NO accounts on the device.
# Build first: ./gradlew assembleDebug
set -e
PKG=com.bold.launcher

adb install -t app/build/outputs/apk/debug/app-debug.apk

# 1. Device Owner (fails if the phone has Google/other accounts: remove them or factory reset)
adb shell dpm set-device-owner $PKG/.AdminReceiver

# 2. Windowing: let apps be forced into the top-region window
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell settings put global hidden_api_policy 1

# 3. Silent IME (blocks Gboard popping up)
adb shell ime enable $PKG/.NoopIme
adb shell ime set $PKG/.NoopIme

# 4. Turn on the accessibility service that draws + types with the keyboard
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.KeyboardService
adb shell settings put secure accessibility_enabled 1

# 5. Notification badges (the listener only reads notifications, it never changes them)
adb shell cmd notification allow_listener $PKG/$PKG.NotifListener

# 6. Freeform flags need a reboot
adb reboot
