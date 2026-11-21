package com.chargetone.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.toneHistoryStore: DataStore<Preferences> by preferencesDataStore(
    name = "tone_history"
)

/**
 * 「使用过的音效」仓库。
 *
 * 存储用 DataStore + 一段 JSON（记录量很小，不值得引入序列化框架）。
 * 只负责记录，不负责删除文件：被挤出上限的条目通过返回值告诉调用方，
 * 由界面层决定是否删除对应文件（仍被槽位引用的不能删）。
 */
class ToneHistoryRepository(context: Context) {

    private val appContext = context.applicationContext

    val entries: Flow<List<ToneHistoryEntry>> = appContext.toneHistoryStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> decode(prefs[KEY]) }

    suspend fun currentBlocking(): List<ToneHistoryEntry> =
        runCatching { entries.first() }.getOrDefault(emptyList())

    /**
     * 记录一次使用。
     * @return 因超出上限被挤出的条目（调用方可据此删除文件）
     */
    suspend fun record(token: String, displayName: String, slot: String): List<ToneHistoryEntry> {
        if (token.isBlank()) return emptyList()
        val entry = ToneHistoryEntry(
            token = token,
            displayName = displayName.ifBlank { token },
            usedAt = System.currentTimeMillis(),
            slot = slot,
        )
        var evicted: List<ToneHistoryEntry> = emptyList()
        appContext.toneHistoryStore.edit { prefs ->
            val merged = ToneHistoryLogic.merge(decode(prefs[KEY]), entry)
            evicted = merged.evicted
            prefs[KEY] = encode(merged.entries)
        }
        return evicted
    }

    suspend fun remove(token: String) {
        appContext.toneHistoryStore.edit { prefs ->
            prefs[KEY] = encode(ToneHistoryLogic.remove(decode(prefs[KEY]), token))
        }
    }

    suspend fun clear() {
        appContext.toneHistoryStore.edit { prefs -> prefs[KEY] = encode(emptyList()) }
    }

    private fun encode(list: List<ToneHistoryEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("token", entry.token)
                    put("name", entry.displayName)
                    put("at", entry.usedAt)
                    put("slot", entry.slot)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<ToneHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val token = item.optString("token").ifBlank { return@mapNotNull null }
                ToneHistoryEntry(
                    token = token,
                    displayName = item.optString("name").ifBlank { token },
                    usedAt = item.optLong("at"),
                    slot = item.optString("slot"),
                )
            }
        }.onFailure { Log.w(TAG, "解析音效历史失败，已忽略", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "ChargeTone/History"
        val KEY = stringPreferencesKey("tone_history_entries")
    }
}
