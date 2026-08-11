package com.example.sportapp.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistance du switch d'URL serveur (admin users uniquement).
 *
 * Stocke 2 valeurs : le preset choisi (PC_LAN/PI_PROD/CUSTOM) et l'URL libre
 * en mode CUSTOM. L'URL effective est resolue par [ServerUrlRepository] au
 * boot pour peupler [com.example.sportapp.core.utils.AppConfig].
 */
private val Context.serverUrlDataStore by preferencesDataStore(name = "server_url_settings")

class ServerUrlDataStore(private val context: Context) {

    private object Keys {
        val PRESET = stringPreferencesKey("preset")
        val CUSTOM_URL = stringPreferencesKey("custom_url")
    }

    data class Snapshot(val preset: ServerUrlPreset, val customUrl: String)

    val snapshotFlow: Flow<Snapshot> = context.serverUrlDataStore.data.map { prefs ->
        Snapshot(
            preset = prefs[Keys.PRESET]
                ?.let { runCatching { ServerUrlPreset.valueOf(it) }.getOrNull() }
                ?: ServerUrlPreset.PI_PROD,
            customUrl = prefs[Keys.CUSTOM_URL].orEmpty(),
        )
    }

    suspend fun snapshot(): Snapshot = snapshotFlow.first()

    suspend fun setPreset(preset: ServerUrlPreset) {
        context.serverUrlDataStore.edit { it[Keys.PRESET] = preset.name }
    }

    suspend fun setCustomUrl(url: String) {
        context.serverUrlDataStore.edit { it[Keys.CUSTOM_URL] = url }
    }
}
