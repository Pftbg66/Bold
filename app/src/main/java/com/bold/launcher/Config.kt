package com.bold.launcher

object Config {
    /** Bottom fraction of the screen reserved for the keyboard. */
    const val KEYBOARD_FRACTION = 0.40f

    /** Weather location (Open-Meteo, no API key). Default is Brampton, Ontario: change to yours. */
    const val WEATHER_LAT = 43.7315
    const val WEATHER_LON = -79.7624
    const val CELSIUS = true

    /** Hide the status bar (and nav bar) everywhere. Clock and battery live in the launcher header. */
    const val HIDE_STATUS_BAR = true

    /** Play our own sound/vibration for new notifications from apps in the launcher. Turn off if you hear doubles. */
    const val OWN_ALERTS = true

    /** Flip these on once everything works. Test with both false first! */
    const val KIOSK = false       // lock task + persistent home
    const val LOCK_DOWN = false   // user restrictions (can block adb installs / resets)

    /** Pin the phone's default dialer, texting, contacts, maps, browser and camera apps to the top. */
    const val PIN_DEFAULT_APPS = true

    /** Extra apps to pin (package names, in order), shown after the defaults. Optional. */
    val FAVORITES: List<String> = emptyList()

    /** Apps that never show in the list. Add Settings here once you're done setting up. */
    val HIDDEN: Set<String> = setOf(
        "com.android.vending", // Play Store
    )
}
