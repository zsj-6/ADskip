package com.appskip.rule

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 规则订阅管理器：下载、缓存、更新规则订阅源。
 */
object SubscriptionManager {
    private const val TAG = "SubMgr"
    private const val CACHE_DIR = "subscriptions"
    private const val PREFS_NAME = "adskip_subs"

    data class SubInfo(
        val id: String,
        val name: String,
        val url: String,
        val lastUpdate: Long = 0,
        val ruleCount: Int = 0,
    )

    interface DownloadCallback {
        fun onSuccess(ruleCount: Int)
        fun onError(message: String)
    }

    fun getRecommendedSubs(): List<SubInfo> {
        return listOf(
            SubInfo(
                id = "gkd_default",
                name = "GKD 订阅 (AIsouler)",
                url = "https://raw.githubusercontent.com/AIsouler/GKD_subscription/main/subscription.json",
            ),
        )
    }

    fun getSavedSubs(context: Context): List<SubInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet("sub_ids", emptySet()) ?: emptySet()
        return ids.mapNotNull { id -> loadSubInfo(prefs, id) }.toList()
    }

    private fun loadSubInfo(prefs: android.content.SharedPreferences, id: String): SubInfo? {
        val name = prefs.getString("sub_name_$id", null) ?: return null
        val url = prefs.getString("sub_url_$id", "") ?: ""
        val lastUpdate = prefs.getLong("sub_update_$id", 0)
        val ruleCount = prefs.getInt("sub_count_$id", 0)
        return SubInfo(id, name, url, lastUpdate, ruleCount)
    }

    fun saveSub(context: Context, sub: SubInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = (prefs.getStringSet("sub_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(sub.id)
        prefs.edit()
            .putStringSet("sub_ids", ids)
            .putString("sub_name_${sub.id}", sub.name)
            .putString("sub_url_${sub.id}", sub.url)
            .putLong("sub_update_${sub.id}", sub.lastUpdate)
            .putInt("sub_count_${sub.id}", sub.ruleCount)
            .apply()
    }

    fun removeSub(context: Context, subId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = (prefs.getStringSet("sub_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(subId)
        prefs.edit()
            .putStringSet("sub_ids", ids)
            .remove("sub_name_$subId")
            .remove("sub_url_$subId")
            .remove("sub_update_$subId")
            .remove("sub_count_$subId")
            .apply()
        getCacheFile(context, subId).delete()
    }

    /**
     * 在后台线程下载订阅规则。
     */
    fun downloadAsync(context: Context, sub: SubInfo, callback: DownloadCallback) {
        Thread {
            try {
                Log.i(TAG, "Downloading from ${sub.url}")
                val url = URL(sub.url)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "ADskip/1.0")

                if (conn.responseCode !in 200..299) {
                    callback.onError("HTTP ${conn.responseCode}")
                    return@Thread
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                // 缓存到文件
                val cacheFile = getCacheFile(context, sub.id)
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(body)

                // 解析
                val rules = RuleLoader.parseRulesJson(body)
                val count = rules.size

                saveSub(context, sub.copy(
                    lastUpdate = System.currentTimeMillis(),
                    ruleCount = count,
                ))

                Log.i(TAG, "Downloaded $count rules from ${sub.name}")
                callback.onSuccess(count)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                callback.onError(e.message ?: "Unknown error")
            }
        }.start()
    }

    fun loadCachedRules(context: Context, subId: String): List<Rule> {
        val cacheFile = getCacheFile(context, subId)
        return if (cacheFile.exists()) {
            RuleLoader.loadFromFile(cacheFile)
        } else {
            emptyList()
        }
    }

    fun loadAllCachedRules(context: Context): List<Rule> {
        val subs = getSavedSubs(context)
        val allRules = mutableListOf<Rule>()
        for (sub in subs) {
            val rules = loadCachedRules(context, sub.id)
            allRules.addAll(rules)
        }
        return allRules
    }

    private fun getCacheFile(context: Context, subId: String): File {
        return File(context.filesDir, "$CACHE_DIR/$subId.json")
    }
}
