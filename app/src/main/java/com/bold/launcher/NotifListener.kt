package com.bold.launcher

import android.app.Notification
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Shared between the listener service and the launcher (same process). */
object NotifCounts {
    @Volatile var counts: Map<String, Int> = emptyMap()
    @Volatile var onChange: (() -> Unit)? = null
}

/** Counts notifications per app for the launcher badges, and optionally plays its own alert. */
class NotifListener : NotificationListenerService() {

    private val lastWhen = HashMap<String, Long>()

    override fun onListenerConnected() = recount()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        recount()
        if (Config.OWN_ALERTS) maybeAlert(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastWhen.remove(sbn.key)
        recount()
    }

    private fun recount() {
        val active = try { activeNotifications } catch (_: Exception) { return } ?: return
        NotifCounts.counts = active
            .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .groupingBy { it.packageName }
            .eachCount()
        NotifCounts.onChange?.invoke()
    }

    /** Plays the default notification sound and/or vibrates, following the phone's ringer mode. */
    private fun maybeAlert(sbn: StatusBarNotification) {
        if (sbn.isOngoing || AppList.load(this).none { it.second == sbn.packageName }) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // Only alert for new notifications or new content, not every progress tick
        val previous = lastWhen.put(sbn.key, n.`when`)
        if (previous != null && previous == n.`when`) return

        // Skip notifications the app marked as silent
        val ranking = Ranking()
        if (currentRanking.getRanking(sbn.key, ranking) &&
            ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        ) return

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> { playSound(); vibrate() }
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> Unit
        }
    }

    private fun playSound() {
        try {
            RingtoneManager.getRingtone(
                this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (_: Exception) { }
    }

    private fun vibrate() {
        try {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
        } catch (_: Exception) { }
    }
}
