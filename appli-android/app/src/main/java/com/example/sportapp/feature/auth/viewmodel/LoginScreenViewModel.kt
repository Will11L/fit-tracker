package com.example.sportapp.feature.auth.viewmodel

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.R
import com.example.sportapp.core.network.LoginResult
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf

@HiltViewModel
class LoginScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val username = mutableStateOf("")
    val password = mutableStateOf("")
    val isLoading = mutableStateOf(false)


    fun login(onSuccess: () -> Unit) {
        if (isLoading.value) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                when (RetrofitInstance.login(username.value.trim(), password.value)) {
                    LoginResult.Success -> {
                        showSnackbar(
                            message = context.getString(R.string.vm_login_success),
                            type = SnackbarType.SUCCESS,
                            duration = SnackbarDuration.Short
                        )
                        onSuccess()
                    }
                    LoginResult.InvalidCredentials -> showSnackbar(
                        message = context.getString(R.string.vm_login_failed),
                        type = SnackbarType.ERROR
                    )
                    LoginResult.NetworkError -> showSnackbar(
                        message = context.getString(R.string.vm_login_network_error),
                        type = SnackbarType.ERROR
                    )
                }
            } finally {
                isLoading.value = false
            }
        }
    }
}
