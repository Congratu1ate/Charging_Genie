package com.chargetone.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.chargetone.app.data.EventLog

/**
 * 1×1 全透明悬浮窗保活（参考 GKD 的做法）。
 *
 * - 需要「显示在其他应用上层」权限（`SYSTEM_ALERT_WINDOW`）
 * - 窗口 `alpha=0`、`1x1`、不可触摸、不可聚焦，用户完全看不到
 * - 持有悬浮窗的进程在系统看来有可见窗口，且 `SYSTEM_ALERT_WINDOW` 是
 *   Android 12+ 后台启动前台服务的官方豁免项，国产 ROM 的电管策略也通常不清理这类进程
 *
 * 由 [ChargingService] 持有：服务在则窗口在，服务销毁则释放。
 */
object KeepAliveOverlay {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun isAttached(): Boolean = overlayView != null

    /** @return 是否已持有悬浮窗（无权限时返回 false，不抛异常） */
    fun acquire(context: Context): Boolean {
        if (overlayView != null) return true
        if (!canDrawOverlays(context)) return false

        val manager = context.getSystemService(WindowManager::class.java) ?: return false
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            alpha = 0f
        }

        val view = View(context)
        return runCatching {
            manager.addView(view, params)
            overlayView = view
            windowManager = manager
            EventLog.add("悬浮窗保活已生效")
            true
        }.getOrElse { error ->
            EventLog.add("悬浮窗保活失败：${error.javaClass.simpleName}")
            false
        }
    }

    fun release() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
        windowManager = null
        EventLog.add("悬浮窗保活已释放")
    }
}
