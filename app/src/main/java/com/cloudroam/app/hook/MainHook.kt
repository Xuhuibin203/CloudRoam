package com.cloudroam.app.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * libxposed 模块入口（API 102）。
 * 注册：app/src/main/resources/META-INF/xposed/java_init.list
 * 框架按 LSPosed 中配置的作用域注入；仅作用域内进程会进入本类。
 */
class MainHook : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        // 每个进程只安装一次 hook
        if (!param.isFirstPackage) return
        log(Log.INFO, TAG, "CloudRoam 已注入：${param.processName} (${param.packageName})")
        try {
            LocationHooks.install(this)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "安装 hook 失败", t)
        }
    }

    companion object {
        const val TAG = "CloudRoam"
    }
}
