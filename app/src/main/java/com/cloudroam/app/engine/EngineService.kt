package com.cloudroam.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cloudroam.app.MainActivity
import com.cloudroam.app.R
import com.cloudroam.app.data.HookConfigWriter
import com.cloudroam.app.data.Settings

/**
 * 伪装引擎前台服务（FR-07），双引擎（FR-10）：
 *
 * FULL  — LSPosed 注入可用时：向 Hook 层推送配置 + 30s 心跳续期。
 * COMPAT — 免 root：系统 Mock Provider 通道，1Hz 注入带噪声的 GPS 测试位置。
 *
 * 失败安全（共享规则）：服务停止/被杀 → FULL 停止心跳自动超时放行；
 * COMPAT 移除测试 Provider，两种模式都不残留伪装。
 */
class EngineService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var compatMode = false
    private var currentPlace: Settings.Place? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (compatMode) {
                    currentPlace?.let { injectCompat(it) }
                    handler.postDelayed(this, COMPAT_INTERVAL_MS)
                } else {
                    if (!HookConfigWriter.refreshExpiry()) {
                        // LSPosed 服务掉线：明示失效，90s 内 Hook 层心跳超时自动放行真实位置
                        EngineController.onError("LSPosed 服务已断开，伪装即将失效，请重新开启")
                        stopSelf()
                        return
                    }
                    handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
                }
            } catch (e: Exception) {
                // 运行期权限被撤销等异常：报错退出而非崩溃
                EngineController.onError("引擎运行异常：${e.message ?: e.javaClass.simpleName}")
                stopSelf()
            }
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
            // 先以前台状态进入再退出，避免 startForegroundService 无通知导致 RemoteServiceException
            startForegroundSafe(null)
            stopSelf()
            return START_NOT_STICKY
        }
        currentPlace = place

        // 引擎选择：LSPosed 服务可用 → FULL；否则降级 COMPAT（FR-10）
        val fullPushed =
            if (HookConfigWriter.bound.value) {
                HookConfigWriter.push(enabled = true, lat = place.lat, lng = place.lng, name = place.name)
            } else false

        if (fullPushed) {
            compatMode = false
            EngineController.onServiceRunning(EngineController.Mode.FULL)
        } else {
            if (!startCompat(place)) {
                stopSelf()
                return START_NOT_STICKY
            }
            compatMode = true
            EngineController.onServiceRunning(EngineController.Mode.COMPAT)
        }

        startForegroundSafe(place)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (compatMode) {
            stopCompat()
        } else {
            HookConfigWriter.disable()
        }
        compatMode = false
        currentPlace = null
        EngineController.onServiceStopped()
        super.onDestroy()
    }

    // ---------- COMPAT 引擎：系统 Mock Provider（FR-10） ----------

    private fun startCompat(place: Settings.Place): Boolean = try {
        val lm = getSystemService(LocationManager::class.java)
            ?: throw IllegalStateException("无法获取系统定位服务")
        // 清理可能的残留注册，保证干净状态
        try {
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }
        lm.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, false, false, false,   // requiresNetwork/Satellite/Cell, monetaryCost
            true, true, true,             // supportsAltitude/Speed/Bearing
            1,   // powerUsage：POWER_USAGE_LOW（Android 11+ 校验：必须 1~3）
            1    // accuracy：ACCURACY_FINE（Android 11+ 校验：必须 1~2）
        )
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        injectCompat(place)
        true
    } catch (_: SecurityException) {
        EngineController.onError(
            "未获模拟位置权限：请到 系统设置 → 开发者选项 → 选择模拟位置信息应用，选择 CloudRoam 后重试"
        )
        false
    } catch (e: Exception) {
        EngineController.onError("兼容模式启动失败：${e.message ?: e.javaClass.simpleName}")
        false
    }

    private fun injectCompat(place: Settings.Place) {
        val lm = getSystemService(LocationManager::class.java) ?: return
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = place.lat + compatJitter()
            longitude = place.lng + compatJitter()
            accuracy = 12f
            altitude = 10.0
            speed = 0f
            bearing = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        try {
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (_: IllegalArgumentException) {
            // Provider 已被系统清理，静默（下次 tick 重建由 startCompat 处理）
        }
    }

    private fun stopCompat() {
        try {
            val lm = getSystemService(LocationManager::class.java)
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            // 已被清理，忽略
        }
    }

    /** 单点轻微噪声（±数米），避免"完美静止"被识别 */
    private fun compatJitter(): Double = (Math.random() - 0.5) * 0.0001

    // ---------- 通知（FR-07） ----------

    private fun startForegroundSafe(place: Settings.Place?) {
        val notif = buildNotification(place)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                ServiceCompat.startForeground(this, NOTIF_ID, notif, 0)
            }
        } catch (e: Exception) {
            // Android 14+ 对 location 类型前台服务强制要求精确定位权限：未授予时降级报错而非崩溃
            if (compatMode) stopCompat() else HookConfigWriter.disable()
            EngineController.onError("请授予 CloudRoam 精确定位权限后重试（${e.message ?: "FGS"}）")
            stopSelf()
        }
    }

    private fun buildNotification(place: Settings.Place?): Notification {
        val modeText = if (compatMode) "兼容模式" else "完整模式"
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
            .setContentTitle("CloudRoam 伪装运行中（$modeText）")
            .setContentText("虚拟位置：${place?.name ?: "未指定"}")
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
        const val COMPAT_INTERVAL_MS = 1_000L
    }
}
