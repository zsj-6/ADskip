package com.appskip.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.appskip.App
import com.appskip.MainActivity
import com.appskip.R

/**
 * 前台保活服务
 *
 * 通过显示持久通知来降低被系统杀死的概率。
 * 通知内容提示用户服务正在运行，点击可跳回主界面。
 */
class KeepAliveService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("ADskip 运行中")
            .setContentText("基于规则引擎自动跳过广告")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
