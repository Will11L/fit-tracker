package com.example.sportapp.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.sportapp.feature.auth.viewmodel.SignupScreenViewModel

@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val vm: SignupScreenViewModel = hiltViewModel()
    val username by vm.username
    val password by vm.password
    val passwordConfirm by vm.passwordConfirm
    val email by vm.email
    val firstName by vm.firstName
    val lastName by vm.lastName
    val isLoading by vm.isLoading

    val canSubmit = !isLoading
            && username.isNotBlank()
            && password.isNotBlank()
            && passwordConfirm.isNotBlank()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
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

            Image(
                painter = painterResource(id = R.drawable.ic_loading_screen),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_signup_title_create),
                style = MaterialTheme.typography.headlineSmall,
                color = appColors.primaryAction
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_signup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = blueMedium
            )

            Spacer(Modifier.height(28.dp))

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CustomTextField(
                        value = username,
                        onValueChange = { vm.username.value = it },
                        placeholder = stringResource(R.string.auth_signup_username_placeholder),
                        label = stringResource(R.string.auth_signup_username_label),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        )
                    )

                    CustomTextField(
                        value = password,
                        onValueChange = { vm.password.value = it },
                        placeholder = stringResource(R.string.auth_password_placeholder),
                        label = stringResource(R.string.auth_signup_password_label),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        )
                    )

                    CustomTextField(
                        value = passwordConfirm,
                        onValueChange = { vm.passwordConfirm.value = it },
                        placeholder = stringResource(R.string.auth_password_placeholder),
                        label = stringResource(R.string.auth_confirm_password),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        )
                    )

                    CustomTextField(
                        value = email,
                        onValueChange = { vm.email.value = it },
                        placeholder = stringResource(R.string.auth_signup_optional_placeholder),
                        label = stringResource(R.string.auth_email),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        )
                    )

                    CustomTextField(
                        value = firstName,
                        onValueChange = { vm.firstName.value = it },
                        placeholder = stringResource(R.string.auth_signup_optional_placeholder),
                        label = stringResource(R.string.auth_first_name),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        )
                    )

                    CustomTextField(
                        value = lastName,
                        onValueChange = { vm.lastName.value = it },
                        placeholder = stringResource(R.string.auth_signup_optional_placeholder),
                        label = stringResource(R.string.auth_last_name),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (canSubmit) vm.signup(onSignupSuccess)
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
                            text = if (isLoading) stringResource(R.string.auth_loading_signup)
                                   else stringResource(R.string.auth_signup_button_create),
                            clickable = canSubmit,
                            onClick = { vm.signup(onSignupSuccess) },
                            backgroundColor = blueMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_have_account_action),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.primaryAction,
                modifier = Modifier.clickable { onBackToLogin() }
            )
        }
    }
}
