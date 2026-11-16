package com.chargetone.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chargetone.app.AppGraph
import com.chargetone.app.service.ChargingService
import com.chargetone.app.ui.theme.ChargeToneTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChargeToneTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 应用处于前台时可以安全启动前台服务：用于开机恢复失败、被系统强杀后用户回到应用时的自愈
        if (AppGraph.settings.currentBlocking().monitorEnabled) {
            runCatching { ChargingService.start(this) }
        }
    }
}
