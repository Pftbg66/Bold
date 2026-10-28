package com.bold.launcher

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView

class MainActivity : Activity() {

    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }
    private val apps = mutableListOf<Pair<String, String>>() // label to package
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var weatherView: TextView
    private lateinit var batteryView: TextView

    private val appAdapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val (label, pkg) = apps[p]
            val count = NotifCounts.counts[pkg] ?: 0
            val name = TextView(this@MainActivity).apply {
                text = label; textSize = 22f; setTextColor(Color.WHITE)
            }
            val badge = TextView(this@MainActivity).apply {
                text = if (count > 99) "99+" else "$count"
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(20, 6, 20, 6)
                background = GradientDrawable().apply { cornerRadius = 40f; setColor(0xFF185FA5.toInt()) }
                visibility = if (count > 0) View.VISIBLE else View.GONE
            }
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 32, 32, 32)
                addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(badge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            batteryView.text = "${level * 100 / scale}%" + if (charging) " ⚡" else ""
        }
    }

    private val weatherTick = object : Runnable {
        override fun run() {
            Weather.fetch(this@MainActivity) { weatherView.text = it }
            handler.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = windowManager.maximumWindowMetrics.bounds
        val kbHeight = (screen.height() * Config.KEYBOARD_FRACTION).toInt()

        loadApps()

        // Header: time + date on the left, weather on the right
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 44f
            setTextColor(Color.WHITE)
            // Escape hatch: long-press the time to release the device (kiosk off, device owner cleared)
            setOnLongClickListener { releaseDevice(); true }
        }
        val date = TextClock(this).apply {
            format12Hour = "EEE, MMM d"; format24Hour = "EEE, MMM d"
            textSize = 14f
            setTextColor(0xFF9E9E9E.toInt())
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock); addView(date)
        }
        weatherView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            text = Weather.cached(this@MainActivity)
        }
        batteryView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF9E9E9E.toInt())
            gravity = Gravity.END
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(weatherView); addView(batteryView)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 40, 32, 16)
            addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(right, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }

        val list = ListView(this).apply {
            adapter = appAdapter
            setOnItemClickListener { _, _, pos, _ -> launch(apps[pos].second) }
        }
        val spacer = View(this) // sits under the keyboard overlay

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, kbHeight))
        })

        // Calling permission: silent for Device Owner, otherwise a one-time prompt
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setPermissionGrantState(
                admin, packageName, Manifest.permission.CALL_PHONE,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } else if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
        }

        if (Config.HIDE_STATUS_BAR) applyStatusBar()
        if (Config.KIOSK && dpm.isDeviceOwnerApp(packageName)) applyKiosk()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(weatherTick)
        handler.post(weatherTick)
        NotifCounts.onChange = { runOnUiThread { appAdapter.notifyDataSetChanged() } }
        loadApps()
        appAdapter.notifyDataSetChanged()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (Config.KIOSK && dpm.isDeviceOwnerApp(packageName)) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        handler.removeCallbacks(weatherTick)
        NotifCounts.onChange = null
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Config.HIDE_STATUS_BAR) hideSystemBars()
    }

    private fun hideSystemBars() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Device Owner can turn the status bar (and the pull-down shade) off for every app. */
    private fun applyStatusBar() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        try {
            @Suppress("DEPRECATION") dpm.setStatusBarDisabled(admin, true)
        } catch (_: Exception) { }
    }

    /** Re-scans the phone, so newly installed apps show up and lock task covers them. */
    private fun loadApps() {
        apps.clear()
        apps.addAll(AppList.load(this))
        if (Config.KIOSK && dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(admin, (apps.map { it.second } + packageName).toTypedArray())
        }
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val screen = windowManager.maximumWindowMetrics.bounds
        val bounds = Rect(0, 0, screen.width(), (screen.height() * (1f - Config.KEYBOARD_FRACTION)).toInt())

        val opts = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        // Hidden API: force freeform windowing mode (5). Needs hidden_api_policy=1 (see setup.sh).
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(opts, 5)
        } catch (_: Throwable) { }
        startActivity(intent, opts.toBundle())
    }

    private fun applyKiosk() {
        dpm.setLockTaskFeatures(
            admin,
            if (Config.HIDE_STATUS_BAR) DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            else DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        )

        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(this, MainActivity::class.java))

        if (Config.LOCK_DOWN) {
            listOf(
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_SAFE_BOOT,
            ).forEach { dpm.addUserRestriction(admin, it) }
        }
    }

    private fun releaseDevice() {
        try { stopLockTask() } catch (_: Exception) {}
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.clearPackagePersistentPreferredActivities(admin, packageName)
            @Suppress("DEPRECATION") dpm.clearDeviceOwnerApp(packageName)
        }
        finishAffinity()
    }
}
