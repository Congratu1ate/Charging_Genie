package com.chargetone.app.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 供界面展示的最近事件记录（同时写入 logcat，便于真机验证时用 adb 观察）。
 * 仅存在于内存中，进程被回收后清空。
 */
object EventLog {

    private const val TAG = "ChargeTone"
    private const val MAX_ENTRIES = 30

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    fun add(message: String) {
        Log.i(TAG, message)
        val stamp = timeFormat.format(Date())
        _events.update { current -> (listOf("$stamp  $message") + current).take(MAX_ENTRIES) }
    }

    fun clear() {
        _events.value = emptyList()
    }
}
