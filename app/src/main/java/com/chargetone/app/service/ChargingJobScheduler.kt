package com.chargetone.app.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.chargetone.app.data.EventLog

/**
 * 充电事件的两层兜底任务。
 *
 * 背景：ColorOS 的 o-kill 会定期杀掉前台服务进程（实测一天 8 次以上），
 * 进程死后再靠系统「冷启动广播」拉起会明显延迟甚至被丢弃，表现就是
 * 「插上过一会儿才响」或「完全不响」。
 *
 * JobScheduler 的 `setRequiresCharging(true)` 由系统在**开始充电**时主动拉起应用，
 * 不依赖进程存活，也不受隐式广播限制——这是 Google 官方推荐的替代方案。
 *
 * - [WAKE_JOB_ID]（一次性，requiresCharging）：插上电源即触发，负责播放充电音并恢复服务
 * - [HOUSEKEEPING_JOB_ID]（周期性 15 分钟）：负责在「未充电」时重新武装上面那个任务，
 *   并在服务已死时尝试复活
 */
object ChargingJobScheduler {

    private const val WAKE_JOB_ID = 4101
    private const val HOUSEKEEPING_JOB_ID = 4102
    private const val HOUSEKEEPING_INTERVAL_MS = 15 * 60 * 1000L

    fun scheduleAll(context: Context) {
        scheduleWakeOnCharge(context)
        scheduleHousekeeping(context)
    }

    /**
     * 安排「插上就触发」的一次性任务。
     * 只在**当前未充电**时安排：如果已经在充电，任务会因为约束立即满足而立刻执行，
     * 反而在用户打开 App 时凭空响一声。
     */
    fun scheduleWakeOnCharge(context: Context) {
        if (isCharging(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val info = JobInfo.Builder(WAKE_JOB_ID, component(context))
            .setRequiresCharging(true)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(info)
        if (result != JobScheduler.RESULT_SUCCESS) {
            EventLog.add("充电兜底任务排期失败：$result")
        }
    }

    fun scheduleHousekeeping(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val info = JobInfo.Builder(HOUSEKEEPING_JOB_ID, component(context))
            .setRequiresCharging(false)
            .setPeriodic(HOUSEKEEPING_INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(info)
    }

    fun isWakeJobScheduled(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        return scheduler.allPendingJobs.any { it.id == WAKE_JOB_ID }
    }

    fun cancelAll(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancelAll()
    }

    fun isCharging(context: Context): Boolean {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.isCharging
        } else {
            false
        }
    }

    fun component(context: Context) =
        ComponentName(context, ChargingWatchJobService::class.java)

    const val JOB_WAKE = WAKE_JOB_ID
    const val JOB_HOUSEKEEPING = HOUSEKEEPING_JOB_ID
}
