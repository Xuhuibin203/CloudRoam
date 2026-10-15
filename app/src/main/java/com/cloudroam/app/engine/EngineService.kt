package com.cloudroam.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cloudroam.app.MainActivity
import com.cloudroam.app.R
import com.cloudroam.app.data.HookConfigWriter
import com.cloudroam.app.data.Settings

/**
 * 伪装引擎前台服务（FR-07）。
 *
 * 运行中：向注入层推送 enabled=true + 心跳续期；通知栏常驻（含"关闭伪装"动作）。
 * 生命周期兜底（共享规则：失败安全）：服务停止 / 被杀后心跳停止，
 * 注入层在有效期超时后自动放行真实数据，伪装绝不残留。
 */
class EngineService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val heartbeat = object : Runnable {
        override fun run() {
            HookConfigWriter.refreshExpiry()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 位置来源：入口通过 Intent 显式传入；磁贴等入口无参时回退本地缓存
        val place = Settings.place.value
            ?: intent?.let {
                val lat = it.getDoubleExtra("lat", Double.NaN)
                val lng = it.getDoubleExtra("lng", Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    Settings.Place(lat, lng, it.getStringExtra("name") ?: "自定义位置")
                } else null
            }
        if (place == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 配置通道不可用 → 按失败安全直接退出，绝不进入"看似开启实际未生效"的状态
        val pushed = HookConfigWriter.push(
            enabled = true,
            lat = place.lat,
            lng = place.lng,
            name = place.name,
        )
        if (!pushed) {
            EngineController.onError("无法连接 LSPosed 服务，请在自检中确认模块已激活")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(place)
        handler.post(heartbeat)
        EngineController.onServiceRunning()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        HookConfigWriter.disable()
        EngineController.onServiceStopped()
        super.onDestroy()
    }

    private fun startForegroundCompat(place: Settings.Place) {
        val notif = buildNotification(place)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            ServiceCompat.startForeground(this, NOTIF_ID, notif, 0)
        }
    }

    private fun buildNotification(place: Settings.Place): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, EngineService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("CloudRoam 伪装运行中")
            .setContentText("虚拟位置：${place.name}")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "关闭伪装", stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "engine_status"
        const val ACTION_STOP = "com.cloudroam.app.action.STOP"
        const val NOTIF_ID = 1
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
