package com.appskip.rule

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.appskip.selector.Selector
import java.util.concurrent.ConcurrentHashMap

/**
 * 规则匹配与执行引擎。
 *
 * 由 SkipAccessibilityService 调用，负责：
 * 1. 根据包名筛选候选规则
 * 2. 选择器链匹配
 * 3. 执行动作（click / clickCenter / back）
 */
class RuleEngine {

    companion object {
        private const val TAG = "RuleEngine"
    }

    /** 所有已加载规则 */
    private val rules = mutableListOf<Rule>()

    /** 每条规则的执行追踪：ruleId -> (执行次数, 上次执行时间) */
    private data class ExecRecord(val count: Int, val lastTime: Long)
    private val execRecords = ConcurrentHashMap<String, ExecRecord>()

    /** 最近处理过的节点指纹，用于去重 */
    private var lastFingerprint = ""
    private var lastFingerprintTime = 0L

    fun loadRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        rules.sortByDescending { it.priority }
        Log.i(TAG, "Loaded ${rules.size} rules")
    }

    fun addRules(newRules: List<Rule>) {
        rules.addAll(newRules)
        rules.sortByDescending { it.priority }
    }

    fun getAllRules(): List<Rule> = rules.toList()

    /**
     * 处理窗口变化事件，匹配规则并执行。
     *
     * @return 匹配到的规则（无论是否执行了动作），null 表示无匹配
     */
    fun process(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        className: String,
        service: android.accessibilityservice.AccessibilityService,
    ): Rule? {
        // 去重：同一窗口不重复处理
        val fingerprint = "$packageName|$className|${rootNode.childCount}"
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint && now - lastFingerprintTime < 2000L) {
            return null
        }
        lastFingerprint = fingerprint
        lastFingerprintTime = now

        // 筛选候选规则：appId 为空（全局规则）或匹配当前包名
        val candidates = rules.filter { rule ->
            rule.appId.isEmpty() || packageName.contains(rule.appId) || rule.appId == packageName
        }

        if (candidates.isEmpty()) return null

        for (rule in candidates) {
            // 检查冷却时间
            val record = execRecords[rule.id]
            if (record != null) {
                if (rule.actionMaximum > 0 && record.count >= rule.actionMaximum) continue
                if (now - record.lastTime < rule.actionCd) continue
            }

            // matchDelay
            if (rule.matchDelay > 0) {
                Thread.sleep(rule.matchDelay)
            }

            // 先检查排除选择器
            val excluded = rule.excludeSelectors.any { selector ->
                selector.find(rootNode) != null
            }
            if (excluded) continue

            // 匹配选择器
            var targetNode: AccessibilityNodeInfo? = null
            var matchedSelector: Selector? = null

            for (selector in rule.matchSelectors) {
                val node = selector.find(rootNode)
                if (node != null) {
                    targetNode = node
                    matchedSelector = selector
                    break
                }
            }

            if (targetNode != null) {
                Log.i(TAG, "Matched rule [${rule.name}] selector=$matchedSelector pkg=$packageName")

                // actionDelay
                if (rule.actionDelay > 0) {
                    Thread.sleep(rule.actionDelay)
                }

                // 执行动作
                val success = executeAction(targetNode, rule.action, service)
                targetNode.recycle()

                // 更新执行记录
                val newCount = (record?.count ?: 0) + 1
                execRecords[rule.id] = ExecRecord(newCount, now)

                if (success) {
                    Log.i(TAG, "Executed [${rule.name}] action=${rule.action}")
                    return rule
                }
            }
        }

        return null
    }

    private fun executeAction(
        node: AccessibilityNodeInfo,
        action: String,
        service: android.accessibilityservice.AccessibilityService,
    ): Boolean {
        return when (action.lowercase()) {
            "click" -> clickWithFallback(node, service)
            "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "none" -> true
            else -> clickWithFallback(node, service)
        }
    }

    /**
     * 先尝试 ACTION_CLICK，失败后降级为坐标中心点击。
     */
    private fun clickWithFallback(
        node: AccessibilityNodeInfo,
        service: android.accessibilityservice.AccessibilityService,
    ): Boolean {
        // 策略1：无障碍点击
        if (node.isClickable) {
            try {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            } catch (e: Exception) {
                Log.w(TAG, "ClickNode failed", e)
            }
        }

        // 策略2：点击父节点
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                try {
                    val ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent.recycle()
                    if (ok) return true
                } catch (e: Exception) {
                    parent.recycle()
                    Log.w(TAG, "ClickParent failed", e)
                }
                break
            }
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        parent?.recycle()

        // 策略3：坐标点击
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()
            return clickAt(centerX, centerY, service)
        }

        return false
    }

    /**
     * 通过 dispatchGesture 在屏幕坐标处点击。
     */
    private fun clickAt(x: Float, y: Float, service: android.accessibilityservice.AccessibilityService): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply { moveTo(x, y) },
                        0,
                        50
                    )
                )
                .build()

            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            service.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    result = true
                    latch.countDown()
                }
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    latch.countDown()
                }
            }, null)

            latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            result
        } catch (e: Exception) {
            Log.e(TAG, "clickAt failed", e)
            false
        }
    }

    /**
     * 重置所有执行记录（用于规则重新加载后）。
     */
    fun resetRecords() {
        execRecords.clear()
        lastFingerprint = ""
        lastFingerprintTime = 0L
    }
}
