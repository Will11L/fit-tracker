package com.example.sportapp.core.utils

import org.json.JSONObject

object JsonUtils {
    /**
     * Récupère une string nullable depuis un JSONObject.
     * Si la clé est absente ou égale à JSON null → retourne null.
     */
    fun JSONObject.getNullableString(key: String): String? {
        return if (!this.has(key) || this.isNull(key)) {
            null
        } else {
            this.getString(key)
        }
    }

    /**
     * Récupère un Float nullable depuis un JSONObject.
     * Si la clé est absente ou égale à JSON null → retourne null.
     * (utile pour les micros nutrition partiellement renseignés selon la source)
     */
    fun JSONObject.getNullableFloat(key: String): Float? {
        return if (!this.has(key) || this.isNull(key)) {
            null
        } else {
            this.getDouble(key).toFloat()
        }
    }
}
