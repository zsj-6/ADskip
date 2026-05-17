package com.appskip.rule

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 规则加载器：从 assets 或文件系统加载 JSON 规则。
 */
object RuleLoader {

    private const val TAG = "RuleLoader"
    private const val DEFAULT_RULES = "default_rules.json"

    /**
     * 加载内置默认规则。
     */
    fun loadDefaultRules(context: Context): List<Rule> {
        return try {
            val json = context.assets.open(DEFAULT_RULES).bufferedReader().use { it.readText() }
            parseRulesJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default rules", e)
            emptyList()
        }
    }

    /**
     * 从文件加载订阅规则。
     */
    fun loadFromFile(file: File): List<Rule> {
        return try {
            val json = file.readText()
            parseRulesJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules from ${file.path}", e)
            emptyList()
        }
    }

    /**
     * 解析规则 JSON。
     *
     * 支持两种格式：
     * 1. 简单数组: [{"name":"...", "matches":["..."], ...}, ...]
     * 2. 完整订阅源: {"version": 1, "rules": [...], "globalRules": [...]}
     */
    fun parseRulesJson(json: String): List<Rule> {
        val trimmed = json.trim()
        return when {
            trimmed.startsWith("[") -> {
                val arr = org.json.JSONArray(trimmed)
                (0 until arr.length()).map { i ->
                    Rule.fromJson(arr.getJSONObject(i))
                }
            }
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                val rulesArr = obj.optJSONArray("rules") ?: org.json.JSONArray()
                val appRules = (0 until rulesArr.length()).map { i ->
                    Rule.fromJson(rulesArr.getJSONObject(i))
                }
                val globalArr = obj.optJSONArray("globalRules") ?: org.json.JSONArray()
                val globalRules = (0 until globalArr.length()).map { i ->
                    Rule.fromJson(globalArr.getJSONObject(i))
                }
                appRules + globalRules
            }
            else -> emptyList()
        }
    }

    /**
     * 保存订阅规则到文件。
     */
    fun saveToFile(rules: List<Rule>, file: File) {
        try {
            val arr = org.json.JSONArray()
            rules.forEach { rule ->
                val obj = JSONObject().apply {
                    put("name", rule.name)
                    put("appId", rule.appId)
                    if (rule.matches.isNotEmpty()) {
                        put("matches", org.json.JSONArray(rule.matches))
                    }
                    if (rule.excludeMatches.isNotEmpty()) {
                        put("excludeMatches", org.json.JSONArray(rule.excludeMatches))
                    }
                    put("action", rule.action)
                    if (rule.actionDelay > 0) put("actionDelay", rule.actionDelay)
                    if (rule.actionMaximum > 0) put("actionMaximum", rule.actionMaximum)
                    if (rule.actionCd > 0) put("actionCd", rule.actionCd)
                    if (rule.matchDelay > 0) put("matchDelay", rule.matchDelay)
                    if (rule.priority != 0) put("priority", rule.priority)
                }
                arr.put(obj)
            }
            file.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save rules", e)
        }
    }
}
