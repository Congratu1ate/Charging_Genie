package com.chargetone.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChargeToneApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        seedToneHistoryIfEmpty()
    }

    /**
     * 0.3.0 新增「使用过的音效」。老用户升级后历史是空的，
     * 这里把当前三个槽位正在用的音效补记为历史条目，避免「明明在用却没有记录」。
     */
    private fun seedToneHistoryIfEmpty() {
        appScope.launch {
            runCatching {
                if (AppGraph.toneHistory.currentBlocking().isNotEmpty()) return@runCatching
                val settings = AppGraph.settings.currentBlocking()
                val slots = listOf(
                    "charge" to settings.chargeTone,
                    "unplug" to settings.unplugTone,
                    "full" to settings.fullTone,
                )
                slots.forEach { (slot, token) ->
                    if (!token.isNullOrBlank() && AppGraph.toneStore.resolve(token) != null) {
                        AppGraph.toneHistory.record(
                            token = token,
                            displayName = AppGraph.toneStore.displayName(token) ?: token,
                            slot = slot,
                        )
                    }
                }
            }
        }
    }
}
