package com.example.sportapp.core.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * TypeConverters partages pour Room.
 *
 * Une seule instance Gson reutilisee (couteux a construire). fromJson est
 * defendu par try/catch : un payload corrompu ou un format obsolete (ex.
 * notification ancienne avec valeurs numeriques apres bascule
 * Map<String,String>) ne doit jamais crasher l'app au query Room.
 */
private object ConvertersGson {
    val gson: Gson = Gson()
}

class InstructionsConverter {
    @TypeConverter
    fun fromList(value: List<String>?): String? =
        value?.let { ConvertersGson.gson.toJson(it) }

    @TypeConverter
    fun toList(value: String?): List<String>? {
        if (value == null) return null
        return try {
            ConvertersGson.gson.fromJson(
                value,
                object : TypeToken<List<String>>() {}.type
            )
        } catch (e: JsonSyntaxException) {
            Log.w("InstructionsConverter", "Invalid JSON, returning null: ${e.message}")
            null
        }
    }
}

/**
 * Converter Map<String, String> <-> JSON.
 * Signature stricte String->String (vs Map<String,Any> historique) pour
 * eviter l'ambiguite Gson (numbers deserialises en Double par defaut).
 * Les callsites qui veulent stocker des Int doivent les .toString().
 */
class NotificationDataConverter {
    @TypeConverter
    fun fromMap(value: Map<String, String>?): String? =
        value?.let { ConvertersGson.gson.toJson(it) }

    @TypeConverter
    fun toMap(value: String?): Map<String, String>? {
        if (value == null) return null
        return try {
            ConvertersGson.gson.fromJson(
                value,
                object : TypeToken<Map<String, String>>() {}.type
            )
        } catch (e: JsonSyntaxException) {
            Log.w("NotificationDataConverter", "Invalid JSON, returning null: ${e.message}")
            null
        }
    }
}

/**
 * Converter List<Int>? <-> JSON string. Utilise pour Task.recurrenceWeekdays
 * (Phase 0 2026-05-12) qui stocke des [0..6] (Mon=0..Sun=6) pour les taches
 * WEEKLY. Postgres cote serveur utilise JSONB ; SQLite cote Android utilise
 * une string TEXT avec serialisation Gson.
 */
class IntListConverter {
    @TypeConverter
    fun fromList(value: List<Int>?): String? =
        value?.let { ConvertersGson.gson.toJson(it) }

    @TypeConverter
    fun toList(value: String?): List<Int>? {
        if (value == null) return null
        return try {
            ConvertersGson.gson.fromJson(
                value,
                object : TypeToken<List<Int>>() {}.type
            )
        } catch (e: JsonSyntaxException) {
            Log.w("IntListConverter", "Invalid JSON, returning null: ${e.message}")
            null
        }
    }
}
