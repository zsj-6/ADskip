package com.appskip.util

import android.content.Context
import android.content.SharedPreferences

class SettingsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("appskip_prefs", Context.MODE_PRIVATE)

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("first_launch", true)
        set(value) = prefs.edit().putBoolean("first_launch", value).apply()

    var totalSkipCount: Long
        get() = prefs.getLong("total_skip_count", 0L)
        set(value) = prefs.edit().putLong("total_skip_count", value).apply()

    var enableSplashSkip: Boolean
        get() = prefs.getBoolean("enable_splash_skip", true)
        set(value) = prefs.edit().putBoolean("enable_splash_skip", value).apply()

    var enableShakeSkip: Boolean
        get() = prefs.getBoolean("enable_shake_skip", true)
        set(value) = prefs.edit().putBoolean("enable_shake_skip", value).apply()

    var showNotification: Boolean
        get() = prefs.getBoolean("show_notification", true)
        set(value) = prefs.edit().putBoolean("show_notification", value).apply()

    var batteryOptimizationShown: Boolean
        get() = prefs.getBoolean("battery_opt_shown", false)
        set(value) = prefs.edit().putBoolean("battery_opt_shown", value).apply()

    fun incrementSkipCount() {
        totalSkipCount++
        prefs.edit().putLong("total_skip_count", totalSkipCount).apply()
    }
}
