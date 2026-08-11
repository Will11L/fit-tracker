package com.example.sportapp.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenManager {
    /** Nom des EncryptedSharedPreferences (V8.2-3). */
    private const val PREFS = "auth_prefs_enc"

    /**
     * Ancien nom des SharedPreferences en clair (avant V8.2-3). Garde
     * pour la migration unique au boot : si on trouve un token legacy
     * la, on le copie dans les EncryptedSharedPreferences puis on
     * supprime les prefs legacy.
     */
    private const val PREFS_LEGACY = "auth_prefs"

    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_REFRESH = "refresh_token"

    private var _token: String? = null
    private var _refreshToken: String? = null

    val token: String? get() = _token
    val refreshToken: String? get() = _refreshToken

    fun init(context: Context) {
        val prefs = encryptedPrefs(context)
        migrateLegacyIfNeeded(context, prefs)
        _token = prefs.getString(KEY_TOKEN, null)
        _refreshToken = prefs.getString(KEY_REFRESH, null)
    }

    fun setToken(context: Context, newToken: String) {
        _token = newToken
        encryptedPrefs(context).edit { putString(KEY_TOKEN, newToken) }
    }

    fun setRefreshToken(context: Context, newRefresh: String) {
        _refreshToken = newRefresh
        encryptedPrefs(context).edit { putString(KEY_REFRESH, newRefresh) }
    }

    /** Atomic update : set both tokens en une fois (login + refresh rotation). */
    fun setTokens(context: Context, accessToken: String, refreshToken: String) {
        _token = accessToken
        _refreshToken = refreshToken
        encryptedPrefs(context).edit {
            putString(KEY_TOKEN, accessToken)
            putString(KEY_REFRESH, refreshToken)
        }
    }

    fun clearToken(context: Context) {
        _token = null
        _refreshToken = null
        encryptedPrefs(context).edit {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH)
        }
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(context)
        } catch (e: Exception) {
            // EncryptedSharedPreferences peut être indéchiffrable après réinstall
            // APK / rotation Android Keystore : AEADBadTagException dans Tink lors
            // du déchiffrement de la masterkey ou des keysets. Bug connu Google
            // (issuetracker 164901843). Recovery : wipe le fichier prefs corrompu
            // + l'entry masterkey Keystore + retry build. Effet pour l'user :
            // perte des tokens stockés -> re-login obligatoire (1 fois).
            Log.w("TokenManager", "Encrypted prefs unreadable, wiping and recreating", e)
            try {
                val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                ks.deleteEntry("_androidx_security_master_key_")
            } catch (_: Exception) {
                // best-effort, ne pas bloquer si le keystore refuse
            }
            context.deleteSharedPreferences(PREFS)
            buildEncryptedPrefs(context)
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Migration unique des SharedPreferences legacy en clair vers les
     * EncryptedSharedPreferences. Lance au 1er init() apres update.
     * Idempotent : si les prefs legacy n'existent pas (ou sont vides),
     * no-op.
     */
    private fun migrateLegacyIfNeeded(context: Context, encPrefs: SharedPreferences) {
        val legacy = context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
        val legacyToken = legacy.getString(KEY_TOKEN, null)
        val legacyRefresh = legacy.getString(KEY_REFRESH, null)

        if (legacyToken == null && legacyRefresh == null) return

        encPrefs.edit {
            if (legacyToken != null) putString(KEY_TOKEN, legacyToken)
            if (legacyRefresh != null) putString(KEY_REFRESH, legacyRefresh)
        }
        legacy.edit {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH)
        }
        Log.i("TokenManager", "Migrated legacy auth_prefs -> encrypted auth_prefs_enc")
    }
}
