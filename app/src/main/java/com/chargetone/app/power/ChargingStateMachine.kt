package com.chargetone.app.power

import android.os.SystemClock
import com.chargetone.app.data.EventLog

/**
 * 充电状态去重 / 防抖。
 *
 * 插拔充电器的瞬间，系统可能连续抛出多条广播，部分机型还会在
 * ACTION_POWER_CONNECTED 与 ACTION_BATTERY_CHANGED 之间反复切换状态。
 * 这里只保留「真正的状态变化」，并丢弃时间窗内的抖动。
 *
 * 接收器始终在主线程回调，因此无需额外加锁。
 */
object ChargingStateMachine {

    /** 抖动窗口：小于该间隔的状态翻转视为噪声 */
    private const val DEBOUNCE_MS = 1_200L

    /** null 表示「进程刚启动，状态未知」，此时第一条事件一定被采纳 */
    private var lastConnected: Boolean? = null
    private var lastEventAt: Long = 0L

    data class Transition(
        /** true = 接入充电器，false = 拔出充电器 */
        val connected: Boolean,
        /** BatteryManager.EXTRA_PLUGGED：0 无、1 AC、2 USB、4 无线 */
        val plugged: Int,
    )

    /**
     * @return 需要响应的状态变化；返回 null 表示这是重复事件或抖动，应忽略。
     */
    fun onEvent(connected: Boolean, plugged: Int, now: Long = SystemClock.elapsedRealtime()): Transition? {
        if (lastConnected == connected) {
            EventLog.add("忽略重复事件（状态未变化，仍为 ${if (connected) "接入" else "断开"}）")
            return null
        }
        if (lastEventAt != 0L && now - lastEventAt < DEBOUNCE_MS) {
            EventLog.add("忽略抖动事件（距上次 ${now - lastEventAt}ms）")
            return null
        }
        lastConnected = connected
        lastEventAt = now
        return Transition(connected = connected, plugged = plugged)
    }

    /** 当前已知的充电状态；null 表示进程刚启动、尚未收到过事件 */
    fun isCurrentlyConnected(): Boolean? = lastConnected

    /** 仅用于测试：重置内部状态 */
    fun reset() {
        lastConnected = null
        lastEventAt = 0L
    }
}
