package com.chargetone.app.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.chargetone.app.data.EventLog
import com.chargetone.app.power.ChargingEventHandler

/**
 * 兜底任务的执行入口，见 [ChargingJobScheduler] 的说明。
 *
 * 关键点：任务只在前台服务**不可用**时才动手。服务活着时，插拔由动态接收器实时处理，
 * 任务再做一次只会造成重复（虽然状态机也会去重）。
 */
class ChargingWatchJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        when (params?.jobId) {
            ChargingJobScheduler.JOB_WAKE -> handleWakeOnCharge()
            ChargingJobScheduler.JOB_HOUSEKEEPING -> handleHousekeeping()
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    /** 系统在开始充电时拉起本进程：补播充电音，并尝试恢复前台服务 */
    private fun handleWakeOnCharge() {
        if (MonitorStatus.running.value) return

        EventLog.add("充电任务触发：服务未运行，补播充电音并恢复监听")
        ChargingEventHandler.handle(this, connected = true)
        runCatching { ChargingService.start(this) }
            .onFailure { EventLog.add("任务中恢复服务失败：${it.javaClass.simpleName}") }
    }

    /** 周期性巡检：未充电时重新武装「插上就响」的任务；服务死了就尝试复活 */
    private fun handleHousekeeping() {
        if (!ChargingJobScheduler.isCharging(this)) {
            ChargingJobScheduler.scheduleWakeOnCharge(this)
            return
        }
        if (!MonitorStatus.running.value) {
            runCatching { ChargingService.start(this) }
                .onFailure { EventLog.add("巡检恢复服务失败：${it.javaClass.simpleName}") }
        }
    }
}
