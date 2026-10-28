package com.bold.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/** Discovers every launchable app on the phone. Returns (label, package) pairs. */
object AppList {

    fun load(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val found = pm.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != ctx.packageName && it.second !in Config.HIDDEN }
            .distinctBy { it.second }

        // Pinned apps first (in order), then everything else A to Z
        val pinned = (Config.FAVORITES + if (Config.PIN_DEFAULT_APPS) defaultApps(ctx) else emptyList()).distinct()
        val top = pinned.mapNotNull { p -> found.firstOrNull { it.second == p } }
        val rest = found.filter { it.second !in pinned }.sortedBy { it.first.lowercase() }
        return top + rest
    }

    /** The phone's own default dialer, texting, contacts, maps, browser and camera apps, whatever they're called. */
    private fun defaultApps(ctx: Context): List<String> {
        val pm = ctx.packageManager
        fun resolve(i: Intent): String? =
            pm.resolveActivity(i, 0)?.activityInfo?.packageName?.takeIf { it != "android" }

        return listOfNotNull(
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            Telephony.Sms.getDefaultSmsPackage(ctx),
            resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)),
            resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
            resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        )
    }
}
