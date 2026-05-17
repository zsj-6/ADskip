# 保留无障碍服务类
-keep class com.appskip.service.SkipAccessibilityService { *; }
-keep class com.appskip.service.KeepAliveService { *; }

# 保留广播接收器
-keep class com.appskip.util.BootReceiver { *; }

# 保留 Kotlin 相关
-keepattributes *Annotation*
-keep class kotlin.** { *; }
