package com.chargetone.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import com.chargetone.app.data.EventLog
import java.io.File

/**
 * 提示音播放器。
 *
 * 使用通知类音频属性（USAGE_NOTIFICATION_EVENT），因此提示音走「通知音量」，
 * 且在静音 / 勿扰模式下由系统自动静音，符合本项目的产品预期。
 *
 * 播放期间持有一个带超时的 PARTIAL_WAKE_LOCK，避免息屏瞬时休眠打断播放。
 * 整个播放过程不依赖 Service，也不产生常驻通知（路线 A）。
 */
class PlaybackEngine(context: Context) {

    private val appContext = context.applicationContext

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(file: File, volumePercent: Int) {
        stop()

        val volume = (volumePercent.coerceIn(0, 100)) / 100f
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer

        try {
            mediaPlayer.setAudioAttributes(audioAttributes)
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnPreparedListener { prepared ->
                acquireWakeLock()
                prepared.setVolume(volume, volume)
                prepared.start()
                EventLog.add("播放提示音：${file.name}")
            }
            mediaPlayer.setOnCompletionListener { releasePlayer() }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                EventLog.add("播放失败 what=$what extra=$extra")
                releasePlayer()
                true
            }
            mediaPlayer.prepareAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "播放提示音失败: ${file.name}", t)
            EventLog.add("播放失败：${t.javaClass.simpleName}")
            releasePlayer()
        }
    }

    fun stop() {
        releasePlayer()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }.onFailure { Log.w(TAG, "获取唤醒锁失败", it) }
    }

    private fun releasePlayer() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.release() }
        }
        player = null

        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private companion object {
        const val TAG = "ChargeTone/Playback"
        const val WAKE_LOCK_TAG = "ChargeTone:playback"
        const val WAKE_LOCK_TIMEOUT_MS = 20_000L
    }
}
