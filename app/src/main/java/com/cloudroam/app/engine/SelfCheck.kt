package com.cloudroam.app.engine

import android.content.Context
import com.cloudroam.app.data.HookConfigWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 环境自检（FR-01）：四项运行前提逐项检查。
 * 全部核心项通过 → 引擎可开启；任一未通过 → 开关置灰并给出操作指引。
 */
object SelfCheck {

    data class Item(
        val name: String,
        val passed: Boolean,
        val guide: String,
        /** 需人工确认的提示项（不阻断开关） */
        val manual: Boolean = false,
    )

    suspend fun run(context: Context): List<Item> = withContext(Dispatchers.IO) {
        val root = shell("id")
        val rootOk = root.first && root.second.contains("uid=0")

        val framework = if (rootOk) shell("ls /data/adb/lspd") else false to "未取得 root，跳过"
        val frameworkOk = framework.first

        val moduleOk = HookConfigWriter.bound.value

        listOf(
            Item(
                name = "root 权限",
                passed = rootOk,
                guide = if (rootOk) "已获取超级用户权限"
                else "请使用 Magisk 等工具授予 CloudRoam 超级用户权限后重试"
            ),
            Item(
                name = "LSPosed 框架",
                passed = frameworkOk,
                guide = if (frameworkOk) "已检测到 LSPosed 运行环境"
                else "请安装 LSPosed（Zygisk 模块）并重启手机后重试"
            ),
            Item(
                name = "CloudRoam 模块已激活",
                passed = moduleOk,
                guide = if (moduleOk) "LSPosed 服务已连接本模块"
                else "请在 LSPosed 管理器 → 模块 中启用 CloudRoam，并重启手机"
            ),
            Item(
                name = "作用域已启用",
                passed = moduleOk,
                manual = true,
                guide = "请人工确认：在 LSPosed → CloudRoam → 作用域中勾选需要伪装的应用；" +
                        "修改作用域后需强制停止目标应用再重新打开（或重启目标应用进程）"
            ),
        )
    }

    /** 核心项 = 非人工确认项；全部通过才允许开启引擎 */
    fun corePassed(items: List<Item>): Boolean =
        items.filterNot { it.manual }.all { it.passed }

    private fun shell(cmd: String): Pair<Boolean, String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val done = p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            false to "su 执行超时"
        } else {
            (p.exitValue() == 0) to (out + err)
        }
    } catch (t: Throwable) {
        false to (t.message ?: "无法执行 su")
    }
}
