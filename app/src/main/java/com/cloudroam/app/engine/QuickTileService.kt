package com.cloudroam.app.engine

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cloudroam.app.MainActivity
import com.cloudroam.app.data.Settings

/**
 * 下拉快捷磁贴开关（FR-07）。
 * 与首页开关、通知栏操作同一引擎状态（状态唯一）。
 */
class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val status = EngineController.status.value
        if (status is EngineController.Status.Running) {
            EngineController.stop(this)
        } else {
            // 未选点时引导回主页选点（FR-07：开启前置校验=已选点）
            val place = Settings.place.value
            if (place == null) {
                startActivityAndCollapse(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                EngineController.start(this, place)
            }
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val running = EngineController.status.value is EngineController.Status.Running
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (running) {
            Settings.place.value?.name ?: "运行中"
        } else {
            "已关闭"
        }
        tile.updateTile()
    }
}
