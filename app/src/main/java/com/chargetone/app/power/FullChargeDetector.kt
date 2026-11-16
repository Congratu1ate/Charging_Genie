package com.chargetone.app.power

import android.os.BatteryManager

/**
 * 「充满电」检测。
 *
 * 与插拔事件不同，充满是一个**状态**而不是一次广播：系统会在电量变化时
 * 持续发送 `ACTION_BATTERY_CHANGED`（粘性广播，只能动态注册）。这里把它
 * 收敛成「每个充电周期只通知一次」：
 *
 * - 已接入电源且电量达到 100%（或系统状态为 FULL）→ 通知一次；
 * - 拔出电源 → 重置，下一次充电到满电时可以再通知；
 * - 服务启动时用当前电量做一次 priming，避免刚打开应用就突然播放。
 */
object FullChargeDetector {

    private var notifiedThisSession = false

    /** 服务/监听器启动时调用：按当前状态初始化，不触发播放 */
    fun prime(levelPercent: Int, status: Int, plugged: Int) {
        val full = isFull(levelPercent, status)
        notifiedThisSession = plugged != 0 && full
    }

    /**
     * @return true 表示本次「充满」需要通知（每个充电周期仅一次）
     */
    fun shouldNotify(levelPercent: Int, status: Int, plugged: Int): Boolean {
        if (plugged == 0) {
            notifiedThisSession = false
            return false
        }
        if (!isFull(levelPercent, status)) return false
        if (notifiedThisSession) return false
        notifiedThisSession = true
        return true
    }

    /** 拔出电源时显式重置（与 BATTERY_CHANGED 中的 plugged==0 双保险） */
    fun reset() {
        notifiedThisSession = false
    }

    private fun isFull(levelPercent: Int, status: Int): Boolean =
        status == BatteryManager.BATTERY_STATUS_FULL || levelPercent >= 100
}
