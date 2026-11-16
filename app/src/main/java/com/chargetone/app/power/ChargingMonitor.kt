package com.chargetone.app.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.chargetone.app.data.EventLog

/**
 * 动态注册的充电监听器。
 *
 * 只能在进程存活期间工作，因此由 [com.chargetone.app.service.ChargingService]
 * 这个前台服务持有。之所以不用清单注册：`ACTION_POWER_CONNECTED` /
 * `ACTION_POWER_DISCONNECTED` 自 Android 8.0 起不在隐式广播豁免清单内，
 * 清单接收器会被系统直接丢弃（日志中可见 "Background execution not allowed"）。
 *
 * 同时注册 `ACTION_BATTERY_CHANGED`（粘性广播，同样只能动态注册）用于检测充满电。
 */
class ChargingMonitor(private val context: Context) {

    private var receiver: android.content.BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return

        // 先用当前电量做一次 priming：服务刚启动时若已满电，不应立刻响
        readStickyBattery()?.let { intent ->
            FullChargeDetector.prime(
                levelPercent = percentOf(intent),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            )
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val newReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED ->
                        ChargingEventHandler.handle(receiverContext, connected = true)
                    Intent.ACTION_POWER_DISCONNECTED ->
                        ChargingEventHandler.handle(receiverContext, connected = false)
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val percent = percentOf(intent)
                        if (ChargingStateMachine.isCurrentlyConnected() != true) {
                            // 插拔事件还没处理完（BATTERY_CHANGED 可能先于 POWER_CONNECTED 到达）。
                            // 此刻若已满电，说明是「插入时本来就满」，交给充电音即可，不补播充满音。
                            FullChargeDetector.prime(percent, status, plugged)
                        } else if (FullChargeDetector.shouldNotify(percent, status, plugged)) {
                            ChargingEventHandler.handleFullCharge(receiverContext, percent)
                        }
                    }
                }
            }
        }
        // 系统广播仍然会投递到 RECEIVER_NOT_EXPORTED 的动态接收器
        ContextCompat.registerReceiver(
            context,
            newReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = newReceiver
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun readStickyBattery(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun percentOf(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }
}
