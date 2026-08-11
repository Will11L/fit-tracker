package com.example.sportapp.feature.admin.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.feature.admin.data.AdminToggleRequest
import com.example.sportapp.feature.admin.data.AdminUserDto
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * VM de l'écran admin pour gérer is_admin sur les autres users.
 * Vue transient (pas de cache Room) -- fetch direct REST + state in-memory.
 */
@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val api = RetrofitInstance.adminApi

    sealed class UiState {
        data object Loading : UiState()
        data class Loaded(val users: List<AdminUserDto>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Id du user courant -- pour griser le Switch sur sa propre row (self-protect UI). */
    val currentUserId: Int? get() = CurrentUserManager.userId

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val users = api.listUsers().sortedBy { it.username.lowercase() }
                UiState.Loaded(users)
            } catch (e: HttpException) {
                UiState.Error(httpErrorMessage(e))
            } catch (e: Exception) {
                UiState.Error(context.getString(R.string.admin_error_network, e.message ?: e::class.simpleName ?: ""))
            }
        }
    }

    fun toggleAdmin(userId: Int, newValue: Boolean) {
        viewModelScope.launch {
            try {
                val updated = api.toggleAdmin(userId, AdminToggleRequest(isAdmin = newValue))
                val msg = if (newValue)
                    context.getString(R.string.admin_promoted, updated.username)
                else
                    context.getString(R.string.admin_demoted, updated.username)
                showSnackbar(message = msg, type = SnackbarType.SUCCESS, duration = SnackbarDuration.Short)
                // Update optimiste in-place : remplacer l'entrée dans la liste
                // sans repasser par Loading (évite le flash visuel CircularProgress).
                // Le serveur a renvoyé le UserOut updated, on l'utilise directement.
                val current = _state.value
                if (current is UiState.Loaded) {
                    val newList = current.users
                        .map { if (it.id == updated.id) updated else it }
                        .sortedBy { it.username.lowercase() }
                    _state.value = UiState.Loaded(newList)
                }
            } catch (e: HttpException) {
                showSnackbar(
                    message = httpErrorMessage(e),
                    type = SnackbarType.ERROR,
                    duration = SnackbarDuration.Short,
                )
            } catch (e: Exception) {
                showSnackbar(
                    message = context.getString(R.string.admin_error_network, e.message ?: e::class.simpleName ?: ""),
                    type = SnackbarType.ERROR,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    private fun httpErrorMessage(e: HttpException): String {
        return when (e.code()) {
            400 -> e.response()?.errorBody()?.string()?.let { extractDetail(it) }
                ?: context.getString(R.string.admin_error_action_denied)
            403 -> context.getString(R.string.admin_error_not_admin)
            404 -> context.getString(R.string.admin_error_user_not_found)
            else -> context.getString(R.string.admin_error_generic, e.code())
        }
    }

    /** Extrait le champ "detail" d'un JSON d'erreur FastAPI. KISS, pas de Gson ici. */
    private fun extractDetail(body: String): String {
        val key = "\"detail\":\""
        val start = body.indexOf(key)
        if (start < 0) return body.take(200)
        val from = start + key.length
        val end = body.indexOf("\"", from)
        return if (end > from) body.substring(from, end) else body.take(200)
    }
}
