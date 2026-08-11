package com.example.sportapp.feature.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.data.local.UserDao
import com.example.sportapp.core.network.MeDeleteRequest
import com.example.sportapp.core.network.MeProfileUpdateRequest
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.network.UserInfo
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class ProfileScreenViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val registry: SyncRegistry,
    private val userDao: UserDao,
) : ViewModel() {
    val hasUnsyncedData: StateFlow<Boolean> = syncManager.hasUnsyncedData

    /** État du flux de suppression de compte (dialog ProfileScreen). */
    sealed interface DeleteAccountState {
        data object Idle : DeleteAccountState
        data object InProgress : DeleteAccountState
        /** Échec : [messageRes] = string affichée dans le dialog. */
        data class Error(val messageRes: Int) : DeleteAccountState
    }

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState

    /**
     * Supprime le compte serveur (`DELETE /me`), purge toutes les données
     * Room locales, puis déclenche [onDeleted] (navigation logout).
     * En cas d'échec, pose un état [DeleteAccountState.Error] consommé par
     * le dialog (l'user peut corriger son mot de passe et réessayer).
     */
    fun deleteAccount(password: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _deleteState.value = DeleteAccountState.InProgress
            try {
                RetrofitInstance.userService.deleteMe(MeDeleteRequest(password))
                // Compte supprimé côté serveur -> on purge toutes les données
                // locales pour qu'elles ne traînent pas sur le device. Ordre
                // inverse FK (registry.reversed) + User en dernier, comme
                // l'outil dev "Clear DB" (SyncSettingsViewModel).
                registry.reversed.forEach { it.clearLocal() }
                userDao.clearAll()
                _deleteState.value = DeleteAccountState.Idle
                onDeleted()
            } catch (e: HttpException) {
                _deleteState.value = DeleteAccountState.Error(
                    when (e.code()) {
                        403 -> R.string.profile_delete_error_password
                        400 -> R.string.profile_delete_error_last_admin
                        else -> R.string.profile_delete_error_generic
                    }
                )
            } catch (e: Exception) {
                _deleteState.value = DeleteAccountState.Error(R.string.profile_delete_error_generic)
            }
        }
    }

    /** Remet l'état à Idle (fermeture du dialog ou édition du mot de passe). */
    fun resetDeleteState() {
        _deleteState.value = DeleteAccountState.Idle
    }

    /**
     * Met à jour le profil self-only via `PATCH /me/profile` (firstName,
     * lastName + bio). Renvoie le `UserInfo` à jour via [onSuccess] pour que
     * l'écran rafraîchisse son affichage. Champs `null` = inchangés
     * (`exclude_unset` côté serveur).
     */
    fun updateProfile(
        req: MeProfileUpdateRequest,
        onSuccess: (UserInfo) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                onSuccess(RetrofitInstance.userService.updateMeProfile(req))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Update failed")
            }
        }
    }
}
