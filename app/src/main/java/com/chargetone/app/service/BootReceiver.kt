package com.chargetone.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chargetone.app.AppGraph
import com.chargetone.app.data.EventLog

/**
 * 开机后恢复监听。
 *
 * Android 15+ 对部分前台服务类型在 BOOT_COMPLETED 中启动做了限制，
 * 因此这里必须捕获异常：失败时降级为"下次打开应用自动恢复"。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppGraph.settings.currentBlocking().monitorEnabled) return

        runCatching { ChargingService.start(context) }
            .onSuccess { EventLog.add("开机自动恢复监听") }
            .onFailure { EventLog.add("开机恢复监听失败：${it.javaClass.simpleName}，请打开应用恢复") }
    }
}
