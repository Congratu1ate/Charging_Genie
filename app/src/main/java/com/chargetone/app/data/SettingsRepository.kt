package com.chargetone.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "charge_tone_settings"
)

/**
 * 设置的唯一数据源。UI 通过 [settings] 观察，广播接收器通过 [currentBlocking] 同步读取。
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    val settings: Flow<Settings> = appContext.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toSettings() }
        .onEach { SettingsCache.current = it }

    /**
     * 供 BroadcastReceiver / Service 使用的同步读取。
     * 缓存为空（进程刚被拉起）时回退到阻塞读取一次，设置文件很小，耗时可忽略。
     */
    fun currentBlocking(): Settings {
        SettingsCache.current?.let { return it }
        return runCatching { runBlocking { settings.first() } }
            .onFailure { Log.w(TAG, "读取设置失败，使用默认值", it) }
            .getOrDefault(Settings())
    }

    suspend fun update(transform: (Settings) -> Settings) {
        val next = transform(settings.first())
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_CHARGE_ENABLED] = next.chargeEnabled
            prefs[KEY_UNPLUG_ENABLED] = next.unplugEnabled
            prefs[KEY_FULL_ENABLED] = next.fullEnabled
            prefs[KEY_CHARGE_TONE] = next.chargeTone.orEmpty()
            prefs[KEY_UNPLUG_TONE] = next.unplugTone.orEmpty()
            prefs[KEY_FULL_TONE] = next.fullTone.orEmpty()
            prefs[KEY_VOLUME_PERCENT] = next.toneVolumePercent.coerceIn(0, 100)
            prefs[KEY_MONITOR_ENABLED] = next.monitorEnabled
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        chargeEnabled = this[KEY_CHARGE_ENABLED] ?: true,
        unplugEnabled = this[KEY_UNPLUG_ENABLED] ?: true,
        fullEnabled = this[KEY_FULL_ENABLED] ?: true,
        chargeTone = this[KEY_CHARGE_TONE]?.takeIf { it.isNotBlank() },
        unplugTone = this[KEY_UNPLUG_TONE]?.takeIf { it.isNotBlank() },
        fullTone = this[KEY_FULL_TONE]?.takeIf { it.isNotBlank() },
        toneVolumePercent = (this[KEY_VOLUME_PERCENT] ?: 100).coerceIn(0, 100),
        monitorEnabled = this[KEY_MONITOR_ENABLED] ?: false,
    )

    private companion object {
        const val TAG = "ChargeTone/Settings"

        val KEY_CHARGE_ENABLED = booleanPreferencesKey("charge_enabled")
        val KEY_UNPLUG_ENABLED = booleanPreferencesKey("unplug_enabled")
        val KEY_FULL_ENABLED = booleanPreferencesKey("full_enabled")
        val KEY_CHARGE_TONE = stringPreferencesKey("charge_tone")
        val KEY_UNPLUG_TONE = stringPreferencesKey("unplug_tone")
        val KEY_FULL_TONE = stringPreferencesKey("full_tone")
        val KEY_VOLUME_PERCENT = intPreferencesKey("volume_percent")
        val KEY_MONITOR_ENABLED = booleanPreferencesKey("monitor_enabled")
    }
}

/**
 * 内存缓存，让广播接收器无需阻塞即可拿到最新设置。
 */
internal object SettingsCache {
    @Volatile
    var current: Settings? = null
}
