package com.appskip.util

import android.os.Build

/**
 * OPPO / 一加 / Realme 设备兼容工具
 *
 * 这些品牌都运行 ColorOS / OxygenOS / RealmeUI，后台管理策略相似：
 * - 激进的电池优化，会杀掉后台无障碍服务
 * - 需要手动开启自启动权限
 * - 需要在最近任务中锁定应用
 */
object OppoHelper {

    enum class DeviceBrand {
        OPPO, ONEPLUS, REALME, OTHER
    }

    val brand: DeviceBrand
        get() {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            return when {
                manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceBrand.OPPO
                manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceBrand.ONEPLUS
                manufacturer.contains("realme") || brand.contains("realme") -> DeviceBrand.REALME
                else -> DeviceBrand.OTHER
            }
        }

    val isOppoFamily: Boolean
        get() = brand != DeviceBrand.OTHER

    val brandName: String
        get() = when (brand) {
            DeviceBrand.OPPO -> "OPPO (ColorOS)"
            DeviceBrand.ONEPLUS -> "一加 (OxygenOS/ColorOS)"
            DeviceBrand.REALME -> "Realme (RealmeUI)"
            DeviceBrand.OTHER -> Build.MANUFACTURER
        }

    /**
     * 获取电池优化设置引导文本
     */
    fun getBatteryOptimizationGuide(): String {
        return when (brand) {
            DeviceBrand.OPPO -> buildString {
                appendLine("1. 打开「设置」→「电池」")
                appendLine("2. 点击「应用耗电管理」")
                appendLine("3. 找到「广告跳过助手」")
                appendLine("4. 关闭「后台冻结」和「异常耗电自动优化」")
                appendLine("5. 选择「允许后台运行」")
            }
            DeviceBrand.ONEPLUS -> buildString {
                appendLine("1. 打开「设置」→「电池」")
                appendLine("2. 点击「电池优化」")
                appendLine("3. 找到「广告跳过助手」")
                appendLine("4. 选择「不优化」")
            }
            DeviceBrand.REALME -> buildString {
                appendLine("1. 打开「设置」→「电池」")
                appendLine("2. 点击「应用耗电管理」")
                appendLine("3. 找到「广告跳过助手」")
                appendLine("4. 允许后台运行")
            }
            DeviceBrand.OTHER -> buildString {
                appendLine("1. 打开「设置」→「应用」→「特殊应用权限」")
                appendLine("2. 点击「电池优化」")
                appendLine("3. 找到「广告跳过助手」")
                appendLine("4. 选择「不优化」")
            }
        }
    }

    /**
     * 获取自启动设置引导文本
     */
    fun getAutoStartGuide(): String {
        return when (brand) {
            DeviceBrand.OPPO -> buildString {
                appendLine("1. 打开「设置」→「应用」→「自启动」")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 开启「允许自启动」")
                appendLine("4. 同时建议开启「关联启动」")
            }
            DeviceBrand.ONEPLUS -> buildString {
                appendLine("1. 打开「设置」→「应用」→「自启动管理」")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 开启自启动")
            }
            DeviceBrand.REALME -> buildString {
                appendLine("1. 打开「设置」→「应用」→「自启动管理」")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 开启自启动")
            }
            DeviceBrand.OTHER -> buildString {
                appendLine("1. 打开「设置」→「应用」→「自启动」")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 开启自启动（如果有此选项）")
            }
        }
    }

    /**
     * 获取最近任务锁定引导
     */
    fun getLockAppGuide(): String {
        return when (brand) {
            DeviceBrand.OPPO -> buildString {
                appendLine("1. 打开最近任务（从屏幕底部上滑并停留）")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 点击右上角菜单 → 选择「锁定」")
            }
            DeviceBrand.ONEPLUS -> buildString {
                appendLine("1. 打开最近任务")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 长按并选择「锁定」")
            }
            else -> buildString {
                appendLine("1. 打开最近任务")
                appendLine("2. 找到「广告跳过助手」")
                appendLine("3. 锁定该应用以免被清理")
            }
        }
    }
}
