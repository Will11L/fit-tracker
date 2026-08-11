package com.example.sportapp.core.sync.base

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Contrat unique pour chaque entité synchronisable du domaine.
 *
 * Une `SyncableEntity<T>` ré-expose les opérations DAO + API + métadonnées UI
 * dont le `SyncEngine` (push/pull/merge) et le `SyncRegistry` ont besoin pour
 * fonctionner sur n'importe quelle entité sans connaître son type concret.
 *
 * Ajouter une nouvelle entité au système de sync = créer une `SyncableEntity`
 * + l'ajouter au `SyncRegistry`. Aucun code dispatch n'est à toucher ailleurs.
 *
 * Renommé depuis `Syncable<T>` lors de T4.2 Phase 1.1 (2026-05-07).
 */
interface SyncableEntity<T : Any> {

    // ─── Identité & métadonnées (UI Settings + logs + SyncRegistry) ──────────

    /** Nom pluriel utilisé pour les logs et les snackbars (ex: "Muscles"). */
    val entityName: String

    /** Nom lisible singulier pour l'UI (ex: "Muscle"). */
    val displayName: String

    /** Drawable resource pour l'icône de l'entité (UI Settings). */
    val iconRes: Int

    /** Classe Room de l'entité (utilisée pour découvrir [columns] par reflection). */
    val entityClass: KClass<T>

    /**
     * Colonnes auto-découvertes via reflection sur [entityClass]. Override pour
     * personnaliser (ordre, exclusions, largeurs). Cf. [ColumnDiscovery].
     */
    val columns: List<ColumnDef>
        get() = ColumnDiscovery.discoverColumns(entityClass)

    /**
     * Nom de la table SQL dérivé de [entityName] (PascalCase plural → snake_case).
     *
     * NB : on ne peut PAS lire `@Entity(tableName=...)` au runtime parce que
     * Room déclare ses annotations en `AnnotationRetention.BINARY` (stripped
     * de la classe au runtime — non-visibles via Java reflection).
     * La convention matche les 20 entités du projet (Muscles→muscles,
     * ActualWorkoutSets→actual_workout_sets, ExerciseEquipment→exercise_equipment,
     * etc., cf. @Query strings des DAOs). Override possible si exception.
     */
    val sqlTableName: String
        get() = NamingConventions.pascalToSnake(entityName)

    /**
     * PagingSource paramétré (filtre/tri dynamique en SQL via [SupportSQLiteQuery]).
     * Construit côté VM via [SqlQueryBuilder.build] à partir de [columns] et de
     * l'état UI (recherche globale, filtres colonne, tri).
     */
    fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, T>

    /**
     * Récupère une page de lignes via SQL brut (SELECT + WHERE + ORDER BY + LIMIT
     * OFFSET). Utilisé par la data grid Sync Settings (manual paging Excel-style).
     */
    suspend fun selectRowsRaw(query: SupportSQLiteQuery): List<T>

    /**
     * Compte total des lignes matchant le filtre courant (SELECT COUNT(*)). Sert
     * à calculer le nombre de pages + le label "Showing X-Y of Z".
     */
    suspend fun selectCountRaw(query: SupportSQLiteQuery): Int

    /** Si true, le sync de cette entité n'affiche pas de snackbar (ex: Notification). */
    val silent: Boolean get() = false

    /**
     * Si true, l'entité est lue depuis le serveur uniquement : pas de push,
     * pas de delete client. Réservé aux entités gérées exclusivement
     * server-side (ex: User, cf. politique sécurité CLAUDE.md §8 + F8-Q1).
     */
    val readOnly: Boolean get() = false

    // ─── Lecture locale ──────────────────────────────────────────────────────

    /** Flow observable pour l'UI (compteurs Settings, listes). */
    fun observeAll(): Flow<List<T>>

    /** Snapshot suspend pour les opérations sync. Inclut les `pendingDeletion`. */
    suspend fun getAllOnce(): List<T>

    /** Lignes locales `synced=false` à pousser au serveur. */
    suspend fun getUnsyncedLocals(): List<T>

    /** Lignes locales `pendingDeletion=true` à supprimer du serveur. */
    suspend fun getPendingDeletions(): List<T>

    /** Existe-t-il au moins une ligne `synced=false` ? Plus efficace qu'un `getUnsyncedLocals().isNotEmpty()` (SQL `LIMIT 1`). */
    suspend fun hasUnsynced(): Boolean

    // ─── Lecture serveur ─────────────────────────────────────────────────────

    /** Pull complet depuis le serveur (REST GET /xxx). */
    suspend fun getRemote(): List<T>

    // ─── Écriture locale (côté pull / merge) ─────────────────────────────────

    /** Vide la table locale (pull-then-replace). */
    suspend fun clearLocal()

    /**
     * Insère un payload serveur en forçant `synced=true` et `pendingDeletion=false`
     * (la row vient du serveur = par définition synchronisée). Garantit que le
     * prochain `pushAll` ne re-pusse pas inutilement les rows fraîchement reçues.
     */
    suspend fun insertFromServer(item: T)

    /** Insertion bulk d'un payload serveur. Mêmes garanties que `insertFromServer`. */
    suspend fun bulkInsertFromServer(items: List<T>)

    // ─── Écriture serveur (côté push) ────────────────────────────────────────

    /** Push 1-by-1 (fallback si bulk échoue). */
    suspend fun upsert(item: T)

    /** Push bulk (préféré quand possible). */
    suspend fun upsertBulk(items: List<T>)

    /** Suppression côté serveur (REST DELETE /xxx/{uuid}). */
    suspend fun deleteRemote(item: T)

    // ─── Écriture locale (mark / delete) ─────────────────────────────────────

    /** Flippe `synced=true` côté local après push réussi. */
    suspend fun markAsSynced(item: T)

    /** Marque `synced=false` (utilisé par UI Settings pour forcer un re-push). */
    suspend fun markAsUnsynced(item: T)

    /** Marque `pendingDeletion=true` côté local (sera push DELETE au prochain sync). */
    suspend fun markAsPendingDeletion(item: T)

    /** Suppression locale stricte (utilisée après un deleteRemote réussi). */
    suspend fun deleteLocal(item: T)

    // ─── Accesseurs génériques (pour 3-way merge + pruneStaleLocals) ─────────

    /** Clé d'identification unique (= `uuid` pour les 20 entités sync). */
    fun keyOf(item: T): String

    /** Date de dernière modification au format ISO8601 (pour `isRemoteNewer`). */
    fun updatedAtOf(item: T): String?

    /** Flag `synced` (pour `pruneStaleLocals` qui ne doit pas supprimer les locaux non-syncés). */
    fun syncedOf(item: T): Boolean

    /** Flag `pendingDeletion` (pour stats UI Settings). */
    fun pendingDeletionOf(item: T): Boolean
}
