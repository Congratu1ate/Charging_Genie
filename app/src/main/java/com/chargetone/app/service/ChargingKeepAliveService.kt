package com.chargetone.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.chargetone.app.AppGraph
import com.chargetone.app.data.EventLog

/**
 * 可选「极限保活」使用的无障碍服务。
 *
 * 它**不做任何辅助功能动作**：不读取窗口内容（`canRetrieveWindowContent=false`），
 * 也不处理任何事件。存在的唯一目的，是让系统把本应用当作「无障碍服务宿主」，
 * 从而避免被 ColorOS 的 o-kill / HANS 回收——实测普通前台服务进程一天会被杀 8 次以上。
 *
 * 是否开启完全由用户决定；未开启时应用仍按普通前台服务方式工作。
 */
class ChargingKeepAliveService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        EventLog.add("无障碍保活已连接")

        // 无障碍宿主进程通常被允许启动前台服务，顺便把监听恢复起来
        if (AppGraph.settings.currentBlocking().monitorEnabled) {
            runCatching { ChargingService.start(this) }
                .onFailure { EventLog.add("无障碍保活：启动前台服务失败 ${it.javaClass.simpleName}") }
        }
        ChargingJobScheduler.scheduleAll(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 刻意留空：只需要系统绑定本服务来提升进程优先级
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        EventLog.add("无障碍保活已断开")
        return super.onUnbind(intent)
    }
}

/**
 * 无障碍保活的查询与跳转。状态直接问系统，不依赖进程内缓存。
 */
object AccessibilityKeepAlive {

    /** 本应用的无障碍服务当前是否已启用 */
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }

    /** 跳到本应用的无障碍设置详情页，失败则退回到无障碍总列表 */
    fun openSettings(context: Context) {
        val component = android.content.ComponentName(context, ChargingKeepAliveService::class.java)
        val details = Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(details) }
            .onFailure { runCatching { context.startActivity(list) } }
    }

    /** 跳到「显示在其他应用上层」授权页 */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 侧载应用在 Android 13+ 可能被「受限制的设置」拦住，给出提示文案 */
    fun needsRestrictedSettingsNote(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
