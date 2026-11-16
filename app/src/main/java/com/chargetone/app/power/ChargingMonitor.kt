package com.chargetone.app.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.chargetone.app.data.EventLog

/**
 * 动态注册的充电监听器。
 *
 * 只能在进程存活期间工作，因此由 [com.chargetone.app.service.ChargingService]
 * 这个前台服务持有。之所以不用清单注册：`ACTION_POWER_CONNECTED` /
 * `ACTION_POWER_DISCONNECTED` 自 Android 8.0 起不在隐式广播豁免清单内，
 * 清单接收器会被系统直接丢弃（日志中可见 "Background execution not allowed"）。
 */
class ChargingMonitor(private val context: Context) {

    private var receiver: android.content.BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val newReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED ->
                        ChargingEventHandler.handle(receiverContext, connected = true)
                    Intent.ACTION_POWER_DISCONNECTED ->
                        ChargingEventHandler.handle(receiverContext, connected = false)
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
}
