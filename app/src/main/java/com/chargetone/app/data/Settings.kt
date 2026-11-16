package com.chargetone.app.data

/**
 * 用户设置。所有字段都有默认值，DataStore 缺失的键会回退到默认值。
 */
data class Settings(
    /** 是否播放「接入充电器」提示音 */
    val chargeEnabled: Boolean = true,
    /** 是否播放「断开充电器」提示音 */
    val unplugEnabled: Boolean = true,
    /** 充电提示音文件名（位于 filesDir/tones/），null 表示未设置 */
    val chargeTone: String? = null,
    /** 断开充电器提示音文件名（位于 filesDir/tones/），null 表示未设置 */
    val unplugTone: String? = null,
    /** 提示音的额外音量系数，0~100，作用于系统通知音量之上 */
    val toneVolumePercent: Int = 100,
    /** 用户是否开启了后台监听（前台服务），用于重启后恢复 */
    val monitorEnabled: Boolean = false,
)
