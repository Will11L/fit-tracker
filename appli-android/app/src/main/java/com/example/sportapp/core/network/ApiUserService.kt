package com.example.sportapp.core.network

import android.content.Context
import retrofit2.http.GET
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.text.contains

// === Données de l'utilisateur ===
data class UserInfo(
    val id: Int,
    val username: String,
    val isAdmin: Boolean = false,
    // Email reel optionnel (2026-06-06) -- nullable. GET /me renvoie le vrai
    // user.email (ou null), plus l'ancien synthetique {username}@sportapp.com.
    val email: String? = null,
    // T2.2 (2026-05-06) : firstName/lastName ajoutés côté serveur en F6-4
    // (UserOut Pydantic, aliases camelCase). Ferme la boucle V8.3 signup
    // → /me → affichage UI ProfileScreen.
    val firstName: String? = null,
    val lastName: String? = null,
    // Bio (2026-05-11) : alimente Nutrition future + perso. Tous nullable
    // (l'user peut skip step BIO onboarding).
    val birthDate: String? = null,  // ISO "YYYY-MM-DD"
    val sex: String? = null,        // UPPER_CASE MALE/FEMALE/OTHER
    val heightCm: Float? = null,    // canonique cm
    val weightKg: Float? = null,    // canonique kg
)

// === DTO PATCH /me/profile ===
data class MeProfileUpdateRequest(
    @com.google.gson.annotations.SerializedName("email") val email: String? = null,
    @com.google.gson.annotations.SerializedName("firstName") val firstName: String? = null,
    @com.google.gson.annotations.SerializedName("lastName") val lastName: String? = null,
    @com.google.gson.annotations.SerializedName("birthDate") val birthDate: String? = null,
    @com.google.gson.annotations.SerializedName("sex") val sex: String? = null,
    @com.google.gson.annotations.SerializedName("heightCm") val heightCm: Float? = null,
    @com.google.gson.annotations.SerializedName("weightKg") val weightKg: Float? = null,
)

// === DTO DELETE /me ===
data class MeDeleteRequest(
    @com.google.gson.annotations.SerializedName("password") val password: String,
)

// === Retrofit ===
interface ApiUserService {
    @GET("me")
    suspend fun getUserInfo(): UserInfo

    /** B1 onboarding (Welcome step) + futur ProfileScreen edit. Self-only. */
    @retrofit2.http.PATCH("me/profile")
    suspend fun updateMeProfile(@retrofit2.http.Body body: MeProfileUpdateRequest): UserInfo

    /**
     * Suppression de compte self-only. Le body `{password}` re-confirme
     * l'identité. Suppression IRRÉVERSIBLE (cascade serveur sur toutes les
     * données du user). Erreurs : 403 mot de passe incorrect, 400 dernier
     * admin. `@HTTP(hasBody = true)` car `@DELETE` ne supporte pas de body.
     */
    @retrofit2.http.HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@retrofit2.http.Body body: MeDeleteRequest)
}

// === Gestion utilisateur global ===
object CurrentUserManager {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_WEIGHT_KG = "weight_kg"
    private const val KEY_HEIGHT_CM = "height_cm"
    private const val KEY_BIRTH_DATE = "birth_date"
    private const val KEY_SEX = "sex"

    private val _userIdFlow = MutableStateFlow<Int?>(null)
    val userIdFlow: StateFlow<Int?> = _userIdFlow

    private val _isAdminFlow = MutableStateFlow(false)
    /** True si le user courant est admin. Refresh à chaque /me (verifyToken,
     *  NetworkMonitor, ProfileScreen). Persisté dans SharedPreferences pour
     *  survivre au cold start. */
    val isAdminFlow: StateFlow<Boolean> = _isAdminFlow

    private val _weightKgFlow = MutableStateFlow<Float?>(null)
    /** Poids (kg) du user courant, null si non renseigné. Refresh à chaque /me
     *  (verifyToken, NetworkMonitor, ProfileScreen) — la table `users` Room n'est
     *  pas peuplée (User read-only, UserSyncable supprimé F8-Q1), donc le /me est
     *  la source. Persisté pour survivre au cold start. Base des apports g/kg. */
    val weightKgFlow: StateFlow<Float?> = _weightKgFlow

    // Reste du profil /me (taille / naissance / sexe), même source et cycle de vie que weightKg :
    // la table `users` Room n'est pas peuplée → /me est la source. Base de l'estimation du BMR (Santé).
    private val _heightCmFlow = MutableStateFlow<Float?>(null)
    val heightCmFlow: StateFlow<Float?> = _heightCmFlow
    private val _birthDateFlow = MutableStateFlow<String?>(null)
    val birthDateFlow: StateFlow<String?> = _birthDateFlow
    private val _sexFlow = MutableStateFlow<String?>(null)
    val sexFlow: StateFlow<String?> = _sexFlow

    var userId: Int?
        get() = _userIdFlow.value
        private set(value) {
            _userIdFlow.value = value
        }

    val isAdmin: Boolean get() = _isAdminFlow.value

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_USER_ID)) {
            val id = prefs.getInt(KEY_USER_ID, -1)
            if (id != -1) {
                _userIdFlow.value = id
            }
        }
        _isAdminFlow.value = prefs.getBoolean(KEY_IS_ADMIN, false)
        _weightKgFlow.value = if (prefs.contains(KEY_WEIGHT_KG)) prefs.getFloat(KEY_WEIGHT_KG, 0f) else null
        _heightCmFlow.value = if (prefs.contains(KEY_HEIGHT_CM)) prefs.getFloat(KEY_HEIGHT_CM, 0f) else null
        _birthDateFlow.value = prefs.getString(KEY_BIRTH_DATE, null)
        _sexFlow.value = prefs.getString(KEY_SEX, null)
    }

    fun setUserId(context: Context, id: Int) {
        _userIdFlow.value = id
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putInt(KEY_USER_ID, id) }
    }

    fun setUserAdmin(context: Context, admin: Boolean) {
        _isAdminFlow.value = admin
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putBoolean(KEY_IS_ADMIN, admin) }
    }

    /** Met à jour le poids courant (depuis /me). null efface la valeur stockée. */
    fun setWeightKg(context: Context, weightKg: Float?) {
        _weightKgFlow.value = weightKg
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() {
            if (weightKg != null) putFloat(KEY_WEIGHT_KG, weightKg) else remove(KEY_WEIGHT_KG)
        }
    }

    /**
     * Cache le profil complet du /me (poids + taille + naissance + sexe) — appelé à chaque /me
     * (login, reconnexion, refresh Profil). Source de l'estimation du BMR (Santé, distance/calories),
     * la table `users` Room n'étant pas peuplée. null efface la valeur stockée.
     */
    fun setProfile(context: Context, info: UserInfo) {
        setWeightKg(context, info.weightKg)
        _heightCmFlow.value = info.heightCm
        _birthDateFlow.value = info.birthDate
        _sexFlow.value = info.sex
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() {
            if (info.heightCm != null) putFloat(KEY_HEIGHT_CM, info.heightCm) else remove(KEY_HEIGHT_CM)
            if (info.birthDate != null) putString(KEY_BIRTH_DATE, info.birthDate) else remove(KEY_BIRTH_DATE)
            if (info.sex != null) putString(KEY_SEX, info.sex) else remove(KEY_SEX)
        }
    }

    fun clearUserId(context: Context) {
        _userIdFlow.value = null
        _isAdminFlow.value = false
        _weightKgFlow.value = null
        _heightCmFlow.value = null
        _birthDateFlow.value = null
        _sexFlow.value = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() {
            remove(KEY_USER_ID)
            remove(KEY_IS_ADMIN)
            remove(KEY_WEIGHT_KG)
            remove(KEY_HEIGHT_CM)
            remove(KEY_BIRTH_DATE)
            remove(KEY_SEX)
        }
    }
}