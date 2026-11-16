package com.chargetone.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 前台服务的运行状态，供界面展示。
 * 服务与界面同进程，因此用内存状态即可。
 */
object MonitorStatus {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun setRunning(running: Boolean) {
        _running.value = running
    }
}
