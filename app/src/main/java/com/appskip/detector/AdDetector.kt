package com.appskip.detector

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 广告检测引擎
 *
 * 负责遍历无障碍视图树，找到跳过/关闭按钮并执行点击。
 */
object AdDetector {

    private const val TAG = "AdDetector"
    private const val MAX_DEPTH = 50

    data class SkipResult(
        val skipped: Boolean,
        val method: String = "",
        val matchedText: String = "",
        val packageName: String = "",
        val className: String = "",
    )

    /**
     * 入口：检测当前窗口并尝试跳过广告
     */
    fun detectAndSkip(
        rootNode: AccessibilityNodeInfo?,
        packageName: CharSequence?,
        className: CharSequence?,
    ): SkipResult {
        if (rootNode == null) {
            return SkipResult(skipped = false, method = "no_root")
        }

        val pkg = packageName?.toString() ?: ""
        val cls = className?.toString() ?: ""

        // 已知广告 Activity → 全文匹配（ViewId + 文本 + 摇一摇）
        if (AdPatterns.isAdActivity(cls)) {
            Log.d(TAG, "Match ad activity: $cls")
            return trySkip(rootNode, pkg, cls, "ad_activity", fullScan = true)
        }

        // 已知广告 SDK 包名 → 全文匹配
        if (AdPatterns.isAdRelatedPackage(pkg)) {
            Log.d(TAG, "Match ad package: $pkg")
            return trySkip(rootNode, pkg, cls, "ad_package", fullScan = true)
        }

        // 通用窗口 → 只做安全的 ViewId 匹配，不用模糊文本匹配避免误触
        return trySkip(rootNode, pkg, cls, "general_scan", fullScan = false)
    }

    private fun trySkip(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        className: String,
        source: String,
        fullScan: Boolean,
    ): SkipResult {
        // 策略1：按 ViewId 查找跳过按钮（始终安全）
        val idResult = findByViewId(rootNode)
        if (idResult != null) {
            clickNode(idResult)
            idResult.recycle()
            Log.i(TAG, "Skipped by ViewId [$source] -> $className")
            return SkipResult(
                skipped = true,
                method = "view_id",
                packageName = packageName,
                className = className,
            )
        }

        // 以下策略只在已知广告页面使用，避免误触正常界面
        if (!fullScan) {
            return SkipResult(skipped = false, method = "no_full_scan")
        }

        // 策略2：按文本查找跳过按钮
        val textResult = findBySkipText(rootNode)
        if (textResult != null) {
            clickNode(textResult)
            textResult.recycle()
            Log.i(TAG, "Skipped by text [$source] -> $className")
            return SkipResult(
                skipped = true,
                method = "text_match",
                matchedText = textResult.text?.toString() ?: "",
                packageName = packageName,
                className = className,
            )
        }

        // 策略3：摇一摇广告特殊处理
        if (isShakeAd(rootNode)) {
            val closeNode = findShakeCloseButton(rootNode)
            if (closeNode != null) {
                clickNode(closeNode)
                closeNode.recycle()
                Log.i(TAG, "Shake ad closed [$source] -> $className")
                return SkipResult(
                    skipped = true,
                    method = "shake_close",
                    packageName = packageName,
                    className = className,
                )
            }
        }

        return SkipResult(skipped = false, method = "no_match")
    }

    // ---- 查找方法 ----

    /**
     * 深度优先搜索匹配跳过按钮 ViewId 的节点
     */
    private fun findByViewId(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return searchNode(node, depth = 0) { info ->
            if (info.isClickable && AdPatterns.findSkipButtonId(info.viewIdResourceName)) {
                true
            } else {
                false
            }
        }
    }

    /**
     * 深度优先搜索匹配跳过文本的可点击节点
     */
    private fun findBySkipText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 优先找可点击的跳过文本
        val clickable = searchNode(node, depth = 0) { info ->
            info.isClickable && AdPatterns.isSkipText(info.text)
        }
        if (clickable != null) return clickable

        // 如果没找到可点击的，找包含跳过文本的节点并尝试点击其父节点
        val textNode = searchNode(node, depth = 0) { info ->
            AdPatterns.isSkipText(info.text)
        }
        if (textNode != null) {
            val clickableParent = findClickableParent(textNode)
            textNode.recycle()
            return clickableParent
        }

        return null
    }

    /**
     * 检测是否为摇一摇广告
     */
    private fun isShakeAd(node: AccessibilityNodeInfo): Boolean {
        return searchNode(node, depth = 0) { info ->
            AdPatterns.isShakeAdText(info.text)
        } != null
    }

    /**
     * 在摇一摇广告中找关闭按钮
     */
    private fun findShakeCloseButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 优先找可点击的关闭按钮
        return searchNode(node, depth = 0) { info ->
            info.isClickable && AdPatterns.isCloseText(info.text)
        }
    }

    // ---- 节点操作 ----

    /**
     * 通用深度优先搜索
     */
    private fun searchNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null

        if (predicate(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchNode(child, depth + 1, predicate)
            child.recycle()
            if (result != null) return result
        }

        return null
    }

    /**
     * 向上查找可点击的父节点
     */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 10) {
            if (current.isClickable) {
                val result = AccessibilityNodeInfo.obtain(current)
                current.recycle()
                return result
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        current?.recycle()
        return null
    }

    /**
     * 执行点击
     */
    private fun clickNode(node: AccessibilityNodeInfo) {
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "Click failed", e)
        }
    }
}
