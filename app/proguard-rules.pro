# M1 骨架未启用混淆；开启混淆时注意保留以下规则
# Hook 入口经 META-INF/xposed/java_init.list 反射加载
-keep class com.cloudroam.app.hook.** { *; }
# libxposed API 由框架在运行期提供
-keep class io.github.libxposed.** { *; }
-keep class com.amap.api.** { *; }
