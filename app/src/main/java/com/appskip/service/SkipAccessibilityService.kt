package com.appskip.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.appskip.rule.RuleEngine
import com.appskip.rule.RuleLoader
import com.appskip.util.SettingsHelper

/**
 * 广告跳过无障碍服务，基于 GKD 风格的规则引擎。
 */
class SkipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SkipService"
        private const val DEBOUNCE_MS = 300L

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var skipCount = 0L
            private set

        /** 共享规则引擎，MainActivity 可访问以注入规则 */
        val ruleEngine = RuleEngine()
    }
    private var lastProcessTime = 0L
    private var lastFingerprint = ""
    private var lastFingerprintTime = 0L
    private lateinit var settings: SettingsHelper

    override fun onCreate() {
        super.onCreate()
        settings = SettingsHelper(this)

        // 加载默认规则
        val rules = RuleLoader.loadDefaultRules(this)
        ruleEngine.loadRules(rules)
        Log.i(TAG, "Service created, loaded ${rules.size} rules")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Service connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        startKeepAliveService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        // 防抖
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < DEBOUNCE_MS) return
        lastProcessTime = now

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        // 忽略自己
        if (packageName == this.packageName) return

        // 获取根节点
        val rootNode = if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            rootInActiveWindow
        } else {
            event.source
        } ?: return

        try {
            val matched = ruleEngine.process(rootNode, packageName, className, this)
            if (matched != null) {
                skipCount++
                Log.i(TAG, "Skipped! rule=${matched.name}, count=$skipCount, pkg=$packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process error", e)
        } finally {
            if (rootNode != rootInActiveWindow) {
                rootNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "Service destroyed")
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
