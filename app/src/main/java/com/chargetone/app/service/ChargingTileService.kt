package com.chargetone.app.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chargetone.app.AppGraph
import com.chargetone.app.R
import com.chargetone.app.data.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知栏快捷设置磁贴：不用打开应用就能查看/切换监听状态。
 *
 * 对应 GKD 的 GkdTileService 思路——被 ROM 清理后用户能一键拉起来。
 */
class ChargingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val running = MonitorStatus.running.value
        if (running) {
            scope.launch {
                AppGraph.settings.update { it.copy(monitorEnabled = false) }
                ChargingService.stop(this@ChargingTileService)
                EventLog.add("磁贴：已停止监听")
                updateTile()
            }
        } else {
            scope.launch {
                runCatching { ChargingService.start(this@ChargingTileService) }
                    .onFailure { EventLog.add("磁贴启动失败：${it.javaClass.simpleName}") }
                AppGraph.settings.update { it.copy(monitorEnabled = true) }
                EventLog.add("磁贴：已启动监听")
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val running = MonitorStatus.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "充电精灵"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_charge)
            }
        }
        tile.updateTile()
    }
}
