package com.appskip.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appskip.service.KeepAliveService

/**
 * 开机自启广播接收器
 *
 * 注意：OPPO/一加需要用户在系统设置中手动开启自启动权限才能生效。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val keepAliveIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(keepAliveIntent)
            } else {
                context.startService(keepAliveIntent)
            }
        }
    }
}
