package com.appskip.rule

import com.appskip.selector.Selector
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规则数据模型，匹配 GKD 规则格式。
 */
data class Rule(
    val id: String = "",
    val appId: String = "",           // 目标应用包名
    val name: String = "",            // 规则名称
    val matches: List<String>,        // 选择器列表（命中任一即触发）
    val excludeMatches: List<String> = emptyList(),
    val action: String = "click",     // click | back | none
    val actionDelay: Long = 0,        // 动作延迟 (ms)
    val actionMaximum: Int = 0,       // 最大执行次数 (0=无限制)
    val actionCd: Long = 1000,        // 冷却时间 (ms)
    val matchDelay: Long = 0,         // 匹配延迟 (ms)
    val priority: Int = 0,            // 优先级（越高越先匹配）
) {
    /** 已解析的命中选择器 */
    val matchSelectors: List<Selector> by lazy {
        matches.map { Selector(it) }
    }

    /** 已解析的排除选择器 */
    val excludeSelectors: List<Selector> by lazy {
        excludeMatches.map { Selector(it) }
    }

    companion object {
        fun fromJson(json: JSONObject): Rule {
            val matchesArr = json.optJSONArray("matches") ?: JSONArray()
            val matches = (0 until matchesArr.length()).map { matchesArr.getString(it) }

            val excludeArr = json.optJSONArray("excludeMatches") ?: JSONArray()
            val excludeMatches = (0 until excludeArr.length()).map { excludeArr.getString(it) }

            return Rule(
                id = json.optString("id", ""),
                appId = json.optString("appId", ""),
                name = json.optString("name", ""),
                matches = matches,
                excludeMatches = excludeMatches,
                action = json.optString("action", "click"),
                actionDelay = json.optLong("actionDelay", 0),
                actionMaximum = json.optInt("actionMaximum", 0),
                actionCd = json.optLong("actionCd", 1000),
                matchDelay = json.optLong("matchDelay", 0),
                priority = json.optInt("priority", 0),
            )
        }
    }
}

/**
 * 规则组（按应用分组）。
 */
data class RuleGroup(
    val appId: String,
    val name: String = "",
    val rules: List<Rule>,
)

/**
 * 订阅源配置。
 */
data class Subscription(
    val id: String,
    val name: String,
    val updateUrl: String,
    val version: Int = 1,
    val rules: List<Rule> = emptyList(),
    val globalRules: List<Rule> = emptyList(),
)
