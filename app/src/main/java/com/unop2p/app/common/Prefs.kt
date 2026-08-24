package com.unop2p.app.common

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "uno_prefs")

/** Local, on-device preferences only — nickname and volumes. No cloud, no game state. */
class Prefs(private val context: Context) {
    private object Keys {
        val nickname = stringPreferencesKey("nickname")
        val voiceVolume = floatPreferencesKey("voice_volume")
        val micDefaultOn = booleanPreferencesKey("mic_default_on")
        val stunEnabled = booleanPreferencesKey("stun_enabled")
        val turnUrl = stringPreferencesKey("turn_url")
        val turnUser = stringPreferencesKey("turn_user")
        val turnCred = stringPreferencesKey("turn_cred")
    }

    val nickname: Flow<String> = context.dataStore.data.map { it[Keys.nickname] ?: "" }
    val voiceVolume: Flow<Float> = context.dataStore.data.map { it[Keys.voiceVolume] ?: 1.0f }
    val micDefaultOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.micDefaultOn] ?: false }

    // ICE / Internet-play settings.
    val stunEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.stunEnabled] ?: true }
    val turnUrl: Flow<String> = context.dataStore.data.map { it[Keys.turnUrl] ?: "" }
    val turnUser: Flow<String> = context.dataStore.data.map { it[Keys.turnUser] ?: "" }
    val turnCred: Flow<String> = context.dataStore.data.map { it[Keys.turnCred] ?: "" }

    suspend fun setNickname(value: String) = context.dataStore.edit { it[Keys.nickname] = value.take(24) }
    suspend fun setVoiceVolume(value: Float) = context.dataStore.edit { it[Keys.voiceVolume] = value.coerceIn(0f, 1f) }
    suspend fun setMicDefaultOn(value: Boolean) = context.dataStore.edit { it[Keys.micDefaultOn] = value }
    suspend fun setStunEnabled(value: Boolean) = context.dataStore.edit { it[Keys.stunEnabled] = value }
    suspend fun setTurn(url: String, user: String, cred: String) = context.dataStore.edit {
        it[Keys.turnUrl] = url.trim().take(200)
        it[Keys.turnUser] = user.trim().take(200)
        it[Keys.turnCred] = cred.trim().take(200)
    }
}
