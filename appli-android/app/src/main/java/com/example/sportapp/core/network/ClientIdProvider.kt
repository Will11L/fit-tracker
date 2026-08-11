package com.example.sportapp.core.network

import android.content.Context
import java.util.UUID

object ClientIdProvider {
    private const val PREF_NAME = "fittracker_prefs"
    private const val KEY_CLIENT_ID = "client_id"

    fun getClientId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var clientId = prefs.getString(KEY_CLIENT_ID, null)
        if (clientId == null) {
            clientId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
        }
        return clientId
    }
}
