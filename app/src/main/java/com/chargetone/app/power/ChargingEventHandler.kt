package com.chargetone.app.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.SystemClock
import com.chargetone.app.AppGraph
import com.chargetone.app.data.EventLog

/**
 * 充电事件处理的核心逻辑，由 [ChargingMonitor] 的动态接收器调用。
 */
object ChargingEventHandler {

    /** 插入电源后的宽限期：这段时间内的「满电」视为「插入时本来就满」 */
    private const val PLUG_GRACE_MS = 3_000L

    /** 最近一次「接入充电器」的时间，用于上面那条宽限判定 */
    private var lastConnectAt = 0L

    fun handle(context: Context, connected: Boolean) {
        val battery = readBattery(context)
        val transition = ChargingStateMachine.onEvent(connected, battery.plugged) ?: return

        if (transition.connected) {
            lastConnectAt = SystemClock.elapsedRealtime()
        }

        EventLog.add(
            "${if (transition.connected) "检测到接入充电器" else "检测到拔出充电器"}" +
                "（${plugLabel(transition.plugged, transition.connected)}）"
        )

        if (!transition.connected) {
            // 拔出即结束本次充电周期，下一次充满时可以再响
            FullChargeDetector.reset()
        } else if (FullChargeDetector.isFull(battery.levelPercent, battery.status)) {
            // 插入时设备已满电：本周期不再单独播「充满」音，避免和充电音互相打断
            FullChargeDetector.prime(battery.levelPercent, battery.status, transition.plugged)
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
        // 刚插入电源就满电：属于「插上时本来就满」，不是「充电完成」，
        // 且此时充电音往往正在播放，再播充满音会互相打断。
        // 用时间宽限判定，不依赖插拔瞬间并不可靠的粘性电量状态。
        if (SystemClock.elapsedRealtime() - lastConnectAt < PLUG_GRACE_MS) {
            FullChargeDetector.markNotified()
            EventLog.add("插入时已满电，跳过充满提示音")
            return
        }

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

    /** 粘性电池广播中的插拔状态、电量与充电状态 */
    private data class BatterySnapshot(
        val plugged: Int,
        val levelPercent: Int,
        val status: Int,
    )

    private fun readBattery(context: Context): BatterySnapshot {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatterySnapshot(plugged = 0, levelPercent = -1, status = -1)
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return BatterySnapshot(
            plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
        )
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
