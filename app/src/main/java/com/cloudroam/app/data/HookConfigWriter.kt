package com.cloudroam.app.data

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hook 配置下发通道（FR-05）。
 *
 * App 进程通过 LSPosed 服务把配置写入"远程偏好设置"；
 * 注入层（目标应用进程内）只读同一组键值。
 * 失败安全：expireAt 心跳超时后，注入层自动放行真实数据。
 */
object HookConfigWriter {

    /** 与 hook 侧 LocationHooks.GROUP 保持一致 */
    const val GROUP = "cloudroam"

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()

    @Volatile
    private var service: XposedService? = null

    fun init() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(s: XposedService) {
                service = s
                _bound.value = true
            }

            override fun onServiceDied(s: XposedService) {
                if (service === s) {
                    service = null
                    _bound.value = false
                }
            }
        })
    }

    fun push(
        enabled: Boolean,
        lat: Double = 0.0,
        lng: Double = 0.0,
        name: String = "",
        accuracy: Float = 15f,
    ): Boolean {
        val s = service ?: return false
        s.getRemotePreferences(GROUP).edit()
            .putBoolean("enabled", enabled)
            .putString("lat", lat.toString())
            .putString("lng", lng.toString())
            .putString("name", name)
            .putFloat("accuracy", accuracy)
            .putLong("expireAt", if (enabled) System.currentTimeMillis() + EXPIRE_MS else 0L)
            .apply()
        return true
    }

    /** 前台服务心跳：超过有效期未刷新，注入层自动恢复真实位置 */
    fun refreshExpiry(): Boolean {
        val s = service ?: return false
        s.getRemotePreferences(GROUP).edit()
            .putLong("expireAt", System.currentTimeMillis() + EXPIRE_MS)
            .apply()
        return true
    }

    fun disable(): Boolean = push(enabled = false)

    const val EXPIRE_MS = 90_000L
}
