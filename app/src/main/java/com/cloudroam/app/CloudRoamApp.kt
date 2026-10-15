package com.cloudroam.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.location.LocationManager
import com.cloudroam.app.data.HookConfigWriter
import com.cloudroam.app.data.Settings
import com.cloudroam.app.engine.EngineService

class CloudRoamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 失败安全兜底：进程曾被杀时 onDestroy 不保证执行，
        // 这里清理可能残留的 COMPAT 模式 test provider（无残留时抛异常，忽略）
        try {
            getSystemService(LocationManager::class.java)
                .removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }
        // 数据层与配置通道初始化
        Settings.init(this)
        HookConfigWriter.init()
        // 前台服务通知渠道（FR-07：运行中常驻通知，低优先级不发声）
        val channel = NotificationChannel(
            EngineService.CHANNEL_ID,
            "伪装运行状态",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
