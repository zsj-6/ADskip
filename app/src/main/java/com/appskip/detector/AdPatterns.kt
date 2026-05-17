package com.appskip.detector

/**
 * 已知广告 SDK 特征库
 *
 * 覆盖主流广告 SDK:
 * - 穿山甲 (Pangle/ByteDance)
 * - 广点通 (Tencent GDT)
 * - 快手 (KuaiShou)
 * - 百度 (Baidu MobAds)
 * - Sigmob
 * - 网易 (NetEase)
 * - 阿里 (Alibaba/TAN)
 */
object AdPatterns {

    // ---- 广告 Activity 类名特征 ----

    private val splashActivityPatterns = listOf(
        // 穿山甲 Pangle
        "com.bytedance.sdk.openadsdk",
        "com.bytedance.sdk",
        "com.bytedance.ads",
        "com.pgl.sdk",
        // 广点通 GDT
        "com.qq.e.tg",
        "com.qq.e.ads",
        "com.qq.e.comm",
        // 快手 KuaiShou
        "com.kwad.sdk",
        "com.kwad.components",
        "com.kuaishou",
        // 百度 Baidu
        "com.baidu.mobads",
        "com.baidu.mobad",
        // Sigmob
        "com.sigmob.sdk",
        "com.sigmob.windad",
        // 网易 NetEase
        "com.netease.nis.sdk",
        "com.netease.ad",
        // 阿里 TAN
        "com.alibaba.analytics",
        "com.alimama.mobile",
        // 游可赢
        "com.youke.win",
        // Mintegral
        "com.mbridge.msdk",
        // Unity Ads
        "com.unity3d.ads",
        // Vungle
        "com.vungle.warren",
        // AdMob (Google)
        "com.google.android.gms.ads",
        // AppLovin
        "com.applovin.sdk",
        // 京东
        "com.jd.ad",
        // TapTap
        "com.taptap.sdk",
    )

    // ---- 跳过按钮常见 View ID ----

    private val skipButtonIds = listOf(
        // 穿山甲
        "tt_splash_skip_btn",
        "tt_splash_skip",
        "tt_skip_view",
        "tt_splash_ad_skip",
        "tt_splash_skip_countdown",
        "tt_countdown_view",
        "tt_reward_ad_close",
        "tt_close_btn",
        "tt_video_ad_close",
        // 广点通
        "gdt_skip_view",
        "gdt_skip_button",
        "gdt_countdown",
        "gdt_close_button",
        "gdt_ad_close",
        // 快手
        "ksad_skip_button",
        "ksad_splash_skip",
        "ksad_close_btn",
        "ksad_countdown",
        // 百度
        "baidumobad_skip",
        "bd_ad_skip",
        "mobads_skip",
        "mobads_close",
        // 通用
        "skip_button",
        "skip_btn",
        "skip_view",
        "skip_text",
        "ad_skip_button",
        "ad_skip_btn",
        "splash_skip",
        "close_button",
        "close_btn",
        "iv_close",
        "img_close",
        "btn_close",
        "tv_skip",
        "tv_close",
        "count_down",
        "countdown_text",
    )

    // ---- 跳过按钮文本匹配 ----

    val skipTexts = listOf(
        "跳过",
        "关闭",
        "×",
        "×",
        "x",
        "X",
        "skip",
        "Skip",
        "SKIP",
        "close",
        "Close",
        "CLOSE",
        "知道了",
        "我知道了",
        "确定",
        "取消",
        "关闭广告",
        "跳过广告",
    )

    // ---- 摇一摇广告特征文本 ----

    val shakeAdTexts = listOf(
        "摇一摇",
        "摇动",
        "摇动手机",
        "扭动",
        "扭一扭",
        "晃动",
        "晃动手机",
        "点击跳转",
        "点击了解更多",
        "点击查看详情",
        "第三方应用",
    )

    // ---- 摇一摇广告关闭按钮特征文本 ----

    val shakeCloseTexts = listOf(
        "关闭",
        "×",
        "×",
        "关闭广告",
        "不感兴趣",
        "跳过",
        "我知道了",
    )

    // ---- 常见开屏广告倒计时文本正则 ----

    val countdownPatterns = listOf(
        Regex("""跳过\s*\d+\s*s""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*s\s*跳过""", RegexOption.IGNORE_CASE),
        Regex("""跳过\s*\d+"""),
        Regex("""\d+\s*秒"""),
        Regex("""\d+s""", RegexOption.IGNORE_CASE),
    )

    // ---- 方法 ----

    fun isAdActivity(className: CharSequence?): Boolean {
        if (className.isNullOrBlank()) return false
        return splashActivityPatterns.any { className.contains(it, ignoreCase = true) }
    }

    fun isAdRelatedPackage(packageName: CharSequence?): Boolean {
        if (packageName.isNullOrBlank()) return false
        // 大部分广告SDK的包名都包含 ad/sdk/ads 关键字
        val adIndicators = listOf("ad", "ads", "sdk", "mobads", "mobad")
        val lower = packageName.toString().lowercase()
        return adIndicators.any { lower.contains(it) } || isAdActivity(packageName)
    }

    fun findSkipButtonId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        val lower = viewId.lowercase()
        return skipButtonIds.any { lower.contains(it.lowercase()) }
    }

    fun isSkipText(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.toString().trim()
        // 先检查是否包含跳过关键字（支持 "3s/跳过"、"跳过 3s" 等）
        if (skipTexts.any { trimmed.contains(it, ignoreCase = true) }) return true
        // 再检查正则（倒计时样式）
        if (countdownPatterns.any { it.containsMatchIn(trimmed) }) return true
        // 纯数字倒计时 + 任何分隔符 + 跳过
        if (Regex("""\d+\s*s?\s*[\/\|｜]?\s*跳过""").containsMatchIn(trimmed)) return true
        // 跳过 + 分隔符 + 数字倒计时
        if (Regex("""跳过\s*[\/\|｜]?\s*\d+\s*s?""").containsMatchIn(trimmed)) return true
        return false
    }

    fun isShakeAdText(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.toString().trim()
        return shakeAdTexts.any { trimmed.contains(it, ignoreCase = true) }
    }

    fun isCloseText(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.toString().trim()
        return shakeCloseTexts.any { trimmed.equals(it, ignoreCase = true) }
    }
}
