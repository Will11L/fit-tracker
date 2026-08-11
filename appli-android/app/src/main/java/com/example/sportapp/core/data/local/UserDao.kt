package com.example.sportapp.core.data.local

import androidx.room.*
import com.example.sportapp.core.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * UserDao read-only client (cf. CLAUDE.md §8 politique sécurité).
 *
 * F8-Q1 (2026-05-06) : suppression des champs `synced` / `pendingDeletion`
 * et de toute la logique de sync montante côté client. Les users ne sont
 * jamais push depuis le client : ils sont créés via `/signup` puis
 * read-only (`/me`, `/users/...`). Si un user doit être modifié, ça passe
 * par un endpoint admin serveur, jamais par sync.
 */
@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    fun observeAll(): Flow<List<User>>

    @Query("SELECT * FROM users")
    suspend fun getAllOnce(): List<User>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("DELETE FROM users")
    suspend fun clearAll()

    // --- Insertion depuis le serveur (alias semantique) ---
    suspend fun insertFromServer(user: User) = insert(user)
    suspend fun insertAllFromServer(users: List<User>) = insertAll(users)
}
