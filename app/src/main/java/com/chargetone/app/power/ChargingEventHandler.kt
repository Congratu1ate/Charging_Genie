package com.chargetone.app.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import com.chargetone.app.AppGraph
import com.chargetone.app.data.EventLog

/**
 * 充电事件处理的核心逻辑，由 [ChargingMonitor] 的动态接收器调用。
 */
object ChargingEventHandler {

    fun handle(context: Context, connected: Boolean) {
        val plugged = readPlugged(context)
        val transition = ChargingStateMachine.onEvent(connected, plugged) ?: return

        EventLog.add(
            "${if (transition.connected) "检测到接入充电器" else "检测到拔出充电器"}" +
                "（${plugLabel(transition.plugged, transition.connected)}）"
        )

        if (!transition.connected) {
            // 拔出即结束本次充电周期，下一次充满时可以再响
            FullChargeDetector.reset()
        }

        val settings = AppGraph.settings.currentBlocking()

        if (transition.connected && !settings.chargeEnabled) {
            EventLog.add("充电提示音已关闭，跳过")
            return
        }
        if (!transition.connected && !settings.unplugEnabled) {
            EventLog.add("断开提示音已关闭，跳过")
            return
        }

        val token = if (transition.connected) settings.chargeTone else settings.unplugTone
        play(
            context = context,
            token = token,
            volumePercent = settings.toneVolumePercent,
            label = if (transition.connected) "充电" else "断开",
        )
    }

    fun handleFullCharge(context: Context, percent: Int) {
        val settings = AppGraph.settings.currentBlocking()
        if (!settings.fullEnabled) {
            EventLog.add("充满提示音已关闭，跳过")
            return
        }
        EventLog.add("检测到电量已满（${percent}%），播放充满提示音")
        play(
            context = context,
            token = settings.fullTone,
            volumePercent = settings.toneVolumePercent,
            label = "充满",
        )
    }

    private fun play(context: Context, token: String?, volumePercent: Int, label: String) {
        if (token.isNullOrBlank()) {
            EventLog.add("尚未设置${label}提示音，跳过")
            return
        }
        val file = AppGraph.toneStore.resolve(token)
        if (file == null) {
            EventLog.add("提示音文件不存在：$token")
            return
        }

        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            EventLog.add("当前为静音模式，系统不播放通知类提示音")
            return
        }

        AppGraph.player.play(file, volumePercent)
    }

    /** 读取粘性电池广播中的 EXTRA_PLUGGED：0 无、1 AC、2 USB、4 无线 */
    private fun readPlugged(context: Context): Int {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 0
        return sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }

    private fun plugLabel(plugged: Int, connected: Boolean): String = when {
        plugged == BatteryManager.BATTERY_PLUGGED_AC -> "有线 AC"
        plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
        // 部分机型在广播回调瞬间粘性状态尚未刷新，此时不要错误地写成「未连接电源」
        plugged == 0 && connected -> "已接入（类型未知）"
        plugged == 0 -> "未连接电源"
        else -> "未知电源($plugged)"
    }
}
