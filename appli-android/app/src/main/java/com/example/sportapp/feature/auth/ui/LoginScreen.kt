package com.example.sportapp.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.auth.viewmodel.LoginScreenViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val vm: LoginScreenViewModel = hiltViewModel()
    val username by vm.username
    val password by vm.password
    val isLoading by vm.isLoading

    val canSubmit = !isLoading && username.isNotBlank() && password.isNotBlank()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header
            Image(
                painter = painterResource(id = R.drawable.ic_loading_screen),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(R.string.auth_brand_name),
                style = MaterialTheme.typography.headlineMedium,
                color = appColors.primaryAction
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.auth_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = blueMedium
            )

            Spacer(Modifier.height(56.dp))

            // Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = appColors.bgBottomNav,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    CustomTextField(
                        value = username,
                        onValueChange = { vm.username.value = it },
                        placeholder = stringResource(R.string.auth_username_placeholder),
                        label = stringResource(R.string.auth_username),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    CustomTextField(
                        value = password,
                        onValueChange = { vm.password.value = it },
                        placeholder = stringResource(R.string.auth_password_placeholder),
                        label = stringResource(R.string.auth_password),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus() // optionnel: ferme le clavier
                                if (canSubmit) vm.login(onLoginSuccess)
                            }
                        )
                    )

                    Spacer(Modifier.height(6.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ActionIconWithTextButton(
                            iconRes = R.drawable.ic_account_circle,
                            text = if (isLoading) stringResource(R.string.auth_loading_login)
                                   else stringResource(R.string.auth_login_button),
                            clickable = canSubmit,
                            onClick = { vm.login(onLoginSuccess) },
                            backgroundColor = blueMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_no_account_action),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.primaryAction,
                modifier = Modifier.clickable(enabled = !isLoading) { onCreateAccount() }
            )
        }
    }
}
