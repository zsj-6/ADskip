package com.appskip

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appskip.rule.Rule
import com.appskip.rule.RuleEngine
import com.appskip.rule.RuleLoader
import com.appskip.rule.SubscriptionManager
import com.appskip.service.SkipAccessibilityService
import com.appskip.util.OppoHelper
import com.appskip.util.SettingsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var settingsHelper: SettingsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsHelper = SettingsHelper(this)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun setupClickListeners() {
        findViewById<android.widget.Button>(R.id.btn_open_accessibility).setOnClickListener {
            openAccessibilitySettings()
        }
        findViewById<android.widget.Button>(R.id.btn_battery_optimization).setOnClickListener {
            requestBatteryOptimization()
        }
        findViewById<android.widget.Button>(R.id.btn_auto_start).setOnClickListener {
            openAutoStartSettings()
        }
        findViewById<android.widget.Button>(R.id.btn_add_sub).setOnClickListener {
            addSubscription()
        }
        findViewById<android.widget.Button>(R.id.btn_refresh_rules).setOnClickListener {
            refreshAllRules()
        }

        // 长按URL输入框显示已订阅列表
        findViewById<EditText>(R.id.et_sub_url).setOnLongClickListener {
            showSubList()
            true
        }
    }

    private fun refreshStatus() {
        val serviceEnabled = isAccessibilityServiceEnabled()
        val statusText = findViewById<TextView>(R.id.tv_service_status)
        val statusIndicator = findViewById<android.view.View>(R.id.view_status_indicator)

        if (serviceEnabled) {
            statusText.text = "无障碍服务：已开启"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            statusIndicator.setBackgroundColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "无障碍服务：未开启"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            statusIndicator.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        }

        val totalCount = settingsHelper.totalSkipCount + SkipAccessibilityService.skipCount
        findViewById<TextView>(R.id.tv_skip_count).text = "已跳过广告：$totalCount 次"

        // 规则数量
        val ruleCount = SkipAccessibilityService.ruleEngine.getAllRules().size
        findViewById<TextView>(R.id.tv_rule_count).text = "$ruleCount 条规则"

        findViewById<TextView>(R.id.tv_device_info).text = "设备品牌：${OppoHelper.brandName}"

        val oppoGuideSection = findViewById<android.view.View>(R.id.layout_oppo_guide)
        oppoGuideSection.visibility = if (OppoHelper.isOppoFamily) android.view.View.VISIBLE else android.view.View.GONE

        if (OppoHelper.isOppoFamily) {
            findViewById<TextView>(R.id.tv_battery_guide).text = OppoHelper.getBatteryOptimizationGuide()
            findViewById<TextView>(R.id.tv_autostart_guide).text = OppoHelper.getAutoStartGuide()
            findViewById<TextView>(R.id.tv_lock_guide).text = OppoHelper.getLockAppGuide()
        }
    }

    private fun addSubscription() {
        val urlInput = findViewById<EditText>(R.id.et_sub_url)
        val url = urlInput.text.toString().trim()

        if (url.isEmpty()) {
            // 没有输入URL，添加推荐订阅源
            showRecommendedDialog()
            return
        }

        if (!url.startsWith("http")) {
            Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
            return
        }

        val sub = SubscriptionManager.SubInfo(
            id = url.hashCode().toString(),
            name = url.split("/").lastOrNull()?.take(20) ?: "自定义订阅",
            url = url,
        )

        downloadSub(sub)
    }

    private fun showRecommendedDialog() {
        val recs = SubscriptionManager.getRecommendedSubs()
        val names = recs.map { "${it.name}\n${it.url}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("推荐订阅源")
            .setItems(names) { _, which ->
                val sub = recs[which]
                findViewById<EditText>(R.id.et_sub_url).setText(sub.url)
                downloadSub(sub)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadSub(sub: SubscriptionManager.SubInfo) {
        Toast.makeText(this, "正在下载...", Toast.LENGTH_SHORT).show()

        SubscriptionManager.downloadAsync(this, sub, object : SubscriptionManager.DownloadCallback {
            override fun onSuccess(ruleCount: Int) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "下载成功！$ruleCount 条规则", Toast.LENGTH_SHORT).show()
                    // 重新加载规则到引擎
                    reloadEngineRules()
                    refreshStatus()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "下载失败: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun refreshAllRules() {
        val subs = SubscriptionManager.getSavedSubs(this)
        if (subs.isEmpty()) {
            Toast.makeText(this, "没有订阅源，请先添加", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "更新 ${subs.size} 个订阅源...", Toast.LENGTH_SHORT).show()
        var completed = 0
        var totalRules = 0

        for (sub in subs) {
            SubscriptionManager.downloadAsync(this, sub, object : SubscriptionManager.DownloadCallback {
                override fun onSuccess(ruleCount: Int) {
                    completed++
                    totalRules += ruleCount
                    if (completed >= subs.size) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "更新完成！共 $totalRules 条规则", Toast.LENGTH_SHORT).show()
                            reloadEngineRules()
                            refreshStatus()
                        }
                    }
                }

                override fun onError(message: String) {
                    completed++
                    if (completed >= subs.size) {
                        runOnUiThread {
                            reloadEngineRules()
                            refreshStatus()
                        }
                    }
                }
            })
        }
    }

    private fun reloadEngineRules() {
        val rules = mutableListOf<Rule>()
        // 默认规则
        rules.addAll(RuleLoader.loadDefaultRules(this))
        // 订阅规则
        rules.addAll(SubscriptionManager.loadAllCachedRules(this))
        // 注入引擎
        SkipAccessibilityService.ruleEngine.loadRules(rules)
    }

    private fun showSubList() {
        val subs = SubscriptionManager.getSavedSubs(this)
        if (subs.isEmpty()) {
            Toast.makeText(this, "暂无订阅，请在输入框粘贴 URL 后点「添加订阅」\n或留空点「添加订阅」选推荐源", Toast.LENGTH_LONG).show()
            return
        }

        val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val items = subs.map { sub ->
            val time = if (sub.lastUpdate > 0) dateFmt.format(Date(sub.lastUpdate)) else "未更新"
            "${sub.name}  [${sub.ruleCount}条, $time]"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("已订阅 (长按删除)")
            .setItems(items) { _, which ->
                // 确认删除
                AlertDialog.Builder(this)
                    .setTitle("删除订阅")
                    .setMessage("确定要删除「${subs[which].name}」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        SubscriptionManager.removeSub(this, subs[which].id)
                        reloadEngineRules()
                        refreshStatus()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val serviceName = ComponentName(this, SkipAccessibilityService::class.java)
        return enabledServices.any { it.id == serviceName.flattenToString() }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    @Suppress("DEPRECATION")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
            settingsHelper.batteryOptimizationShown = true
        }
    }

    private fun openAutoStartSettings() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            startActivity(intent)
        } catch (e1: Exception) {
            try {
                val intent = Intent()
                intent.component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.permission.startup.StartupAppListActivity"
                )
                startActivity(intent)
            } catch (e2: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}
