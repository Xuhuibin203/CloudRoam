package com.cloudroam.app.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cloudroam.app.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 引擎状态唯一真值（FR-07 / 共享规则：状态唯一）。
 * 所有入口（首页开关、通知栏、快捷磁贴）操作同一状态。
 */
object EngineController {

    sealed interface Status {
        data object Off : Status
        data object Running : Status
        data class Error(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun start(context: Context, place: Settings.Place) {
        val i = Intent(context.applicationContext, EngineService::class.java)
            .putExtra("lat", place.lat)
            .putExtra("lng", place.lng)
            .putExtra("name", place.name)
        ContextCompat.startForegroundService(context.applicationContext, i)
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, EngineService::class.java)
        )
    }

    fun onServiceRunning() {
        _status.value = Status.Running
    }

    fun onServiceStopped() {
        _status.value = Status.Off
    }

    fun onError(reason: String) {
        _status.value = Status.Error(reason)
    }
}
