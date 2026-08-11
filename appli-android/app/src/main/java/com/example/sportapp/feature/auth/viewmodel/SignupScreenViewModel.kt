package com.example.sportapp.feature.auth.viewmodel

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.network.AuthApi
import com.example.sportapp.core.network.LoginResult
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.network.SignupRequest
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class SignupScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val username = mutableStateOf("")
    val password = mutableStateOf("")
    val passwordConfirm = mutableStateOf("")
    val email = mutableStateOf("")
    val firstName = mutableStateOf("")
    val lastName = mutableStateOf("")
    val isLoading = mutableStateOf(false)

    /** Validation client-side. Le serveur valide aussi (Pydantic min_length). */
    fun validationError(): String? {
        val u = username.value.trim()
        val p = password.value
        val pc = passwordConfirm.value
        return when {
            u.length < 3 -> context.getString(R.string.vm_signup_username_min)
            p.length < 8 -> context.getString(R.string.vm_signup_password_min)
            p != pc -> context.getString(R.string.vm_signup_passwords_mismatch)
            else -> null
        }
    }

    fun signup(onSuccess: () -> Unit) {
        if (isLoading.value) return
        validationError()?.let {
            showSnackbar(message = it, type = SnackbarType.ERROR, duration = SnackbarDuration.Short)
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            try {
                val u = username.value.trim()
                val p = password.value
                RetrofitInstance.authApi.signup(
                    SignupRequest(
                        username = u,
                        password = p,
                        email = email.value.trim().ifBlank { null },
                        firstName = firstName.value.trim().ifBlank { null },
                        lastName = lastName.value.trim().ifBlank { null },
                    )
                )
                // Auto-login : reuse le flow existant qui gere setTokens + isTokenValid.
                val ok = RetrofitInstance.login(u, p) is LoginResult.Success
                if (ok) {
                    showSnackbar(
                        message = context.getString(R.string.vm_signup_welcome, u),
                        type = SnackbarType.SUCCESS,
                        duration = SnackbarDuration.Short,
                    )
                    onSuccess()
                } else {
                    showSnackbar(
                        message = context.getString(R.string.vm_signup_autologin_failed),
                        type = SnackbarType.WARNING,
                    )
                }
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    409 -> context.getString(R.string.vm_signup_username_taken)
                    422 -> context.getString(R.string.vm_signup_invalid_input)
                    429 -> context.getString(R.string.vm_signup_too_many)
                    else -> context.getString(R.string.vm_signup_failed_http, e.code())
                }
                showSnackbar(message = msg, type = SnackbarType.ERROR)
            } catch (e: Exception) {
                showSnackbar(
                    message = context.getString(R.string.vm_signup_error, e.message ?: "unknown"),
                    type = SnackbarType.ERROR,
                )
            } finally {
                isLoading.value = false
            }
        }
    }
}
