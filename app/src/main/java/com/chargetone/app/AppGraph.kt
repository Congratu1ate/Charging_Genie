package com.chargetone.app

import android.content.Context
import com.chargetone.app.audio.PlaybackEngine
import com.chargetone.app.data.SettingsRepository
import com.chargetone.app.data.ToneHistoryRepository
import com.chargetone.app.data.ToneStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简依赖容器。应用只有一个进程，且在 Application.onCreate 中初始化，
 * 因此广播接收器、界面都能安全地通过它取到单例。
 */
object AppGraph {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set
    lateinit var toneStore: ToneStore
        private set
    lateinit var player: PlaybackEngine
        private set
    lateinit var toneHistory: ToneHistoryRepository
        private set

    fun init(context: Context) {
        if (::settings.isInitialized) return
        settings = SettingsRepository(context)
        toneStore = ToneStore(context)
        player = PlaybackEngine(context)
        toneHistory = ToneHistoryRepository(context)

        // 预热设置缓存，让广播接收器可以无阻塞地读取
        appScope.launch {
            runCatching { settings.settings.collect { /* 仅用于填充 SettingsCache */ } }
        }
    }
}
