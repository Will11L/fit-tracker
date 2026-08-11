package com.example.sportapp.app.navigation

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * État global du mode de navigation (Sport / Nutrition / Santé), persisté dans
 * SharedPreferences pour survivre au cold start (critère A7). Miroir Android de
 * `readNavMode` / `writeNavMode` du web (`nav-mode.ts`). Calqué sur
 * [com.example.sportapp.core.network.CurrentUserManager] : `object` + StateFlow
 * observable par la barre basse, lecture synchrone au boot (ex. depuis le splash).
 *
 * C'est une préférence d'appareil (comme le localStorage web) : non liée au user,
 * non effacée au logout.
 */
object NavModeManager {
    private const val PREFS_NAME = "nav_prefs"
    private const val KEY_NAV_MODE = "nav_mode"

    private val _mode = MutableStateFlow(NavMode.SPORT)
    val mode: StateFlow<NavMode> = _mode

    /** Mode courant (lecture synchrone). */
    val current: NavMode get() = _mode.value

    /** Restaure le mode persisté. À appeler au démarrage (SportApp.onCreate). */
    fun init(context: Context) {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NAV_MODE, null)
        _mode.value = NavMode.entries.firstOrNull { it.name == stored } ?: NavMode.SPORT
    }

    /** Pose le mode + le persiste (no-op si déjà à cette valeur). */
    fun setMode(context: Context, mode: NavMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_NAV_MODE, mode.name) }
    }

    /** Le mode suit la page : recale sur la route courante (persiste si change). */
    fun updateFromRoute(context: Context, route: String?) {
        setMode(context, modeForRoute(route))
    }
}
