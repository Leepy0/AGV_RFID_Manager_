package com.example.agv_rfid_manager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// 기존 저장소 이름 유지 (업데이트 후에도 사용자 데이터 보존)
val Context.dataStore by preferencesDataStore(name = "agv_settings")
val Context.RFIDStore by preferencesDataStore(name = "RFID_settings")

object Keys {
    val VIB = booleanPreferencesKey("vib_enabled")
    val SOUND = booleanPreferencesKey("sound_enabled")
    val AUTO_VERIFY = booleanPreferencesKey("auto_verify")
    val IS_KOR = booleanPreferencesKey("is_kor")
    val CMD_TYPES = stringPreferencesKey("cmd_types")
    val SHOW_HISTORY = booleanPreferencesKey("show_history")
    val DARK_MODE = booleanPreferencesKey("dark_mode")      // v1.x 테마 값 (마이그레이션용)
    val THEME_MODE = stringPreferencesKey("theme_mode")     // light / dark / system
    val PERF_MODE = stringPreferencesKey("perf_mode")       // auto / full / low
    val PART1 = stringPreferencesKey("part1")
    val PART2 = stringPreferencesKey("part2")
    val PART3 = stringPreferencesKey("part3")
    val HISTORY = stringPreferencesKey("history_data")

    // 프리셋은 agv_settings 저장소에 보관 (기존과 동일)
    fun preset(i: Int) = stringPreferencesKey("preset_$i")
}

object ThemeMode { const val LIGHT = "light"; const val DARK = "dark"; const val SYSTEM = "system" }
object PerfSetting { const val AUTO = "auto"; const val FULL = "full"; const val LOW = "low" }
