package com.monika.dashboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    // --- Non-sensitive settings via DataStore ---

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val REPORT_INTERVAL = intPreferencesKey("report_interval")
        val HEALTH_SYNC_INTERVAL = intPreferencesKey("health_sync_interval")
        val ENABLED_HEALTH_TYPES = stringSetPreferencesKey("enabled_health_types")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVER_URL] ?: ""
    }

    val reportInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.REPORT_INTERVAL] ?: 60
    }

    val healthSyncInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.HEALTH_SYNC_INTERVAL] ?: 15
    }

    val enabledHealthTypes: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.ENABLED_HEALTH_TYPES] ?: emptySet()
    }

    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MONITORING_ENABLED] ?: false
    }

    suspend fun setServerUrl(url: String) {
        require(validateUrl(url)) { "Invalid URL: must be HTTPS or http://localhost" }
        context.dataStore.edit { it[Keys.SERVER_URL] = url.trim() }
    }

    suspend fun setReportInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.REPORT_INTERVAL] = seconds.coerceIn(10, 300) }
    }

    suspend fun setHealthSyncInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.HEALTH_SYNC_INTERVAL] = minutes.coerceIn(15, 60) }
    }

    suspend fun setEnabledHealthTypes(types: Set<String>) {
        context.dataStore.edit { it[Keys.ENABLED_HEALTH_TYPES] = types }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    // --- Sensitive token via EncryptedSharedPreferences ---

    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SettingsStore", "EncryptedSharedPreferences unavailable", e)
            null
        }
    }

    val isSecureStorageAvailable: Boolean get() = encryptedPrefs != null

    fun getToken(): String? {
        val prefs = encryptedPrefs ?: return null
        return prefs.getString("token", null)
    }

    fun setToken(token: String): Boolean {
        val prefs = encryptedPrefs ?: return false
        return prefs.edit().putString("token", token).commit()
    }

    companion object {
        fun maskToken(token: String): String {
            if (token.length <= 4) return "****"
            return token.take(4) + "***"
        }

        fun validateUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme ?: return false
            val host = uri.host ?: return false
            return when (scheme) {
                "https" -> true
                "http" -> host == "localhost" || host == "127.0.0.1"
                else -> false
            }
        }
    }
}
