package com.chargetone.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chargetone.app.AppGraph
import com.chargetone.app.R
import com.chargetone.app.data.EventLog
import com.chargetone.app.power.ChargingMonitor
import com.chargetone.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 常驻监听服务。
 *
 * 插拔充电器的广播不在隐式广播豁免清单内，清单注册的接收器收不到，
 * 只能由存活进程里的动态接收器监听。前台服务负责让进程一直存活，
 * 代价是一条常驻通知（低优先级，可折叠）。
 *
 * 服务必须由用户在界面上开启（Android 12+ 不允许后台启动前台服务），
 * 之后系统回收进程时会按 START_STICKY 重启。
 */
class ChargingService : Service() {

    private lateinit var monitor: ChargingMonitor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        monitor = ChargingMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { AppGraph.settings.update { it.copy(monitorEnabled = false) } }
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

        val alreadyRunning = MonitorStatus.running.value
        monitor.start()
        MonitorStatus.setRunning(true)
        // 有权限时挂一个 1x1 透明悬浮窗，显著降低被国产 ROM 清理的概率
        KeepAliveOverlay.acquire(this)
        // 每次服务起来都把兜底任务补齐（系统重启、被 o-kill 后自愈时都会重新排期）
        ChargingJobScheduler.scheduleAll(this)
        if (!alreadyRunning) {
            EventLog.add("后台监听服务已启动")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitor.stop()
        KeepAliveOverlay.release()
        MonitorStatus.setRunning(false)
        EventLog.add("后台监听服务已停止")
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "后台监听",
            // LOW 而不是 MIN：MIN 级通知在许多国产 ROM 里等同于「没有通知」，
            // 前台服务的可见性下降后更容易被 o-kill 清理
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持监听插拔充电器事件的常驻通知"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        // 旧的低优先级渠道（重要性已无法修改，只能弃用）
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, ChargingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_charge)
            .setContentTitle("正在监听充电状态")
            .setContentText("插拔充电器时播放你设置的提示音")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "停止监听", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.chargetone.app.action.STOP_MONITOR"

        private const val CHANNEL_ID = "charging_monitor_v2"
        private const val LEGACY_CHANNEL_ID = "charging_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CONTENT = 1
        private const val REQUEST_STOP = 2

        /** 必须在应用处于前台时调用（例如用户点击开关），否则 Android 12+ 会抛异常。 */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChargingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingService::class.java))
        }
    }
}
