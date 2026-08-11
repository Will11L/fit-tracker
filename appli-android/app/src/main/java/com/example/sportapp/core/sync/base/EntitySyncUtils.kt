package com.example.sportapp.core.sync.base

import android.util.Log
import retrofit2.HttpException

// ✅ Fonctions génériques (syncEntity, syncEntityDeletions)

/** Extrait code HTTP + body JSON depuis un Throwable Retrofit pour log lisible. */
private fun Throwable.httpDetail(): String =
    when (this) {
        is HttpException -> {
            val code = code()
            val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
            "HTTP $code body=$body"
        }
        else -> "${this::class.simpleName}: $message"
    }

suspend fun <T : Any> syncEntity(syncable: SyncableEntity<T>): Result<Unit> {
    val unsyncedLocals = syncable.getUnsyncedLocals()
    if (unsyncedLocals.isEmpty()) return Result.success(Unit)

    var hasError = false

    // 1️⃣ Envoie d'abord les modifs locales
    return runCatching {
        syncable.upsertBulk(unsyncedLocals)
    }.onSuccess {
        unsyncedLocals.forEach { item -> syncable.markAsSynced(item) }
    }.onFailure { e ->
        Log.w("Sync", "[${syncable.entityName}] upsertBulk failed (${e.httpDetail()}), fallback to individual upsert")
        unsyncedLocals.forEach { item ->
            runCatching {
                syncable.upsert(item)
            }.onSuccess {
                syncable.markAsSynced(item)
            }.onFailure {
                hasError = true
                Log.e("Sync", "[${syncable.entityName}] upsert failed for $item -- ${it.httpDetail()}")
            }
        }
    }.let {
        if (hasError || it.isFailure) {
            Result.failure(Exception("One or more sync operations failed"))
        } else {
            Result.success(Unit)
        }
    }
}


suspend fun <T : Any> syncEntityDeletions(syncable: SyncableEntity<T>): Result<Unit> {
    val deletions = syncable.getPendingDeletions()
    if (deletions.isEmpty()) return Result.success(Unit)

    var hasError = false

    deletions.forEach { item ->
        runCatching {
            syncable.deleteRemote(item)
        }.onSuccess {
            syncable.deleteLocal(item)  // Suppression locale après succès
        }.onFailure {
            if (it is retrofit2.HttpException && it.code() == 404) {
                Log.w("Sync", "[${syncable.entityName}] item not on server, deleting locally: $item")
                syncable.deleteLocal(item)                // Suppression locale si l'élément n'existe pas sur le serveur
            }
            else {
                hasError = true
                Log.e("Sync", "[${syncable.entityName}] delete failed for $item", it)
            }
        }
    }

    return if (hasError) {
        Result.failure(Exception("One or more deletions failed"))
    } else {
        Log.d("Sync", "[${syncable.entityName}] ${deletions.size} deletions synced")
        Result.success(Unit)
    }
}