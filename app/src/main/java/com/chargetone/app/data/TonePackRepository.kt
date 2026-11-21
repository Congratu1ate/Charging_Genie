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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.tonePackStore: DataStore<Preferences> by preferencesDataStore(
    name = "tone_packs"
)

/**
 * 套装仓库。存储方式与「使用过的音效」一致：DataStore + JSON。
 */
class TonePackRepository(context: Context) {

    private val appContext = context.applicationContext

    val packs: Flow<List<TonePack>> = appContext.tonePackStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> decode(prefs[KEY]) }

    suspend fun currentBlocking(): List<TonePack> =
        runCatching { packs.first() }.getOrDefault(emptyList())

    /** 保存（同名覆盖）。返回被挤出上限的套装 */
    suspend fun save(
        name: String,
        chargeToken: String?,
        unplugToken: String?,
        fullToken: String?,
    ): List<TonePack> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        var evicted: List<TonePack> = emptyList()
        appContext.tonePackStore.edit { prefs ->
            val current = decode(prefs[KEY])
            val existing = current.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            val pack = TonePack(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = trimmed,
                chargeToken = chargeToken,
                unplugToken = unplugToken,
                fullToken = fullToken,
                usedAt = System.currentTimeMillis(),
            )
            val merged = TonePackLogic.upsert(current, pack)
            evicted = merged.evicted
            prefs[KEY] = encode(merged.packs)
        }
        return evicted
    }

    suspend fun touch(id: String) {
        appContext.tonePackStore.edit { prefs ->
            prefs[KEY] = encode(
                TonePackLogic.touch(decode(prefs[KEY]), id, System.currentTimeMillis())
            )
        }
    }

    suspend fun remove(id: String) {
        appContext.tonePackStore.edit { prefs ->
            prefs[KEY] = encode(TonePackLogic.remove(decode(prefs[KEY]), id))
        }
    }

    private fun encode(packs: List<TonePack>): String {
        val array = JSONArray()
        packs.forEach { pack ->
            array.put(
                JSONObject().apply {
                    put("id", pack.id)
                    put("name", pack.name)
                    put("charge", pack.chargeToken.orEmpty())
                    put("unplug", pack.unplugToken.orEmpty())
                    put("full", pack.fullToken.orEmpty())
                    put("at", pack.usedAt)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<TonePack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").ifBlank { return@mapNotNull null }
                TonePack(
                    id = id,
                    name = item.optString("name").ifBlank { "未命名套装" },
                    chargeToken = item.optString("charge").takeIf { it.isNotBlank() },
                    unplugToken = item.optString("unplug").takeIf { it.isNotBlank() },
                    fullToken = item.optString("full").takeIf { it.isNotBlank() },
                    usedAt = item.optLong("at"),
                )
            }
        }.onFailure { Log.w(TAG, "解析套装失败，已忽略", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "ChargeTone/Packs"
        val KEY = stringPreferencesKey("tone_packs")
    }
}
