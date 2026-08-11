package com.example.sportapp.feature.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.network.*
import com.example.sportapp.core.sync.SyncEvents
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.DetailRow
import com.example.sportapp.designsystem.common_components.DialogPrimaryButton
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import com.example.sportapp.feature.profile.ui.components.EditProfileDialog
import com.example.sportapp.feature.profile.viewmodel.ProfileScreenViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: ProfileScreenViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val coroutineScope = rememberCoroutineScope()

    val isTokenValid by RetrofitInstance.isTokenValid.collectAsState()
    val isNetworkAvailable by SyncEvents.isNetworkAvailable.collectAsState()
    val hasUnsynced by viewModel.hasUnsyncedData.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val userId by CurrentUserManager.userIdFlow.collectAsState()

    val token = TokenManager.token
    val context = LocalContext.current
    val clientId = remember { ClientIdProvider.getClientId(context) }

    var userInfo by remember { mutableStateOf<UserInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            userInfo = RetrofitInstance.userService.getUserInfo()
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Failed to load user info"
        }
    }

    val dash = stringResource(R.string.profile_value_not_set)
    val yes = stringResource(R.string.common_yes)
    val no = stringResource(R.string.common_no)
    fun yn(b: Boolean) = if (b) yes else no

    val info = userInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        ScreenTitleBar(title = stringResource(R.string.profile_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🪪 Carte identité : avatar (gauche) + infos en colonne (droite) — 2 colonnes, compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.bgRecessed)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_account_circle),
                    contentDescription = null,
                    tint = appColors.primaryAction,
                    modifier = Modifier.size(64.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val displayName = listOfNotNull(
                        info?.firstName?.takeIf { it.isNotBlank() },
                        info?.lastName?.takeIf { it.isNotBlank() }
                    ).joinToString(" ").ifBlank { info?.username ?: dash }
                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "@${info?.username ?: dash}",
                        fontSize = 14.sp,
                        color = appColors.textTertiary
                    )
                    val admin = info?.isAdmin == true
                    val roleColor = if (admin) blueMedium else lightGrayBlue
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(roleColor.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(if (admin) R.string.profile_role_admin else R.string.profile_role_user),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = roleColor
                        )
                    }
                }
            }

            // 📇 Account
            TitledDivider(stringResource(R.string.profile_section_account))
            ProfileSectionCard {
                DetailRow(
                    iconRes = R.drawable.ic_rounded_mail,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_email),
                    value = info?.email ?: dash
                )
                DetailRow(
                    iconRes = R.drawable.ic_account_circle,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_first_name),
                    value = info?.firstName?.takeIf { it.isNotBlank() } ?: dash
                )
                DetailRow(
                    iconRes = R.drawable.ic_account_circle,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_last_name),
                    value = info?.lastName?.takeIf { it.isNotBlank() } ?: dash
                )
            }

            // 🧬 Bio
            TitledDivider(stringResource(R.string.profile_section_bio))
            val sexValue = when (info?.sex) {
                "MALE" -> stringResource(R.string.onboarding_bio_radio_male)
                "FEMALE" -> stringResource(R.string.onboarding_bio_radio_female)
                "OTHER" -> stringResource(R.string.onboarding_bio_radio_other)
                else -> dash
            }
            ProfileSectionCard {
                DetailRow(
                    iconRes = R.drawable.ic_calendar_today,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_birthdate),
                    value = info?.birthDate?.takeIf { it.isNotBlank() } ?: dash
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_sex),
                    value = sexValue
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_height),
                    value = info?.heightCm?.let { "${trimNum(it)} cm" } ?: dash
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_weight),
                    value = info?.weightKg?.let { "${trimNum(it)} kg" } ?: dash
                )
            }

            // ⚙️ App info (ex-debug)
            TitledDivider(stringResource(R.string.profile_section_app))
            ProfileSectionCard {
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_user_id),
                    value = userId?.toString() ?: dash
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_info,
                    iconColor = appColors.primaryAction,
                    labelColor = appColors.primaryAction,
                    label =stringResource(R.string.profile_field_client_id),
                    value = clientId
                )
                DetailRow(
                    iconRes = R.drawable.ic_rounded_check_circle,
                    iconColor = if (isTokenValid) mediumGreen else orangeMedium,
                    labelColor = appColors.primaryAction,
                    label = stringResource(R.string.profile_field_token),
                    value = yn(isTokenValid),
                    valueColor = if (isTokenValid) mediumGreen else orangeMedium
                )
                DetailRow(
                    iconRes = if (isNetworkAvailable) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off,
                    iconColor = if (isNetworkAvailable) mediumGreen else orangeMedium,
                    labelColor = appColors.primaryAction,
                    label = stringResource(R.string.profile_field_network),
                    value = yn(isNetworkAvailable),
                    valueColor = if (isNetworkAvailable) mediumGreen else orangeMedium
                )
                DetailRow(
                    iconRes = if (hasUnsynced) R.drawable.ic_cloud_off else R.drawable.ic_cloud_done,
                    iconColor = if (hasUnsynced) yellowMedium else mediumGreen,
                    labelColor = appColors.primaryAction,
                    label = stringResource(R.string.profile_field_unsynced),
                    value = yn(hasUnsynced),
                    valueColor = if (hasUnsynced) yellowMedium else mediumGreen
                )
            }

            if (error != null) {
                Text("⚠️ $error", color = redMedium, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))

            // 🔘 Actions : Edit + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_rounded_edit,
                    text = stringResource(R.string.profile_edit),
                    modifier = Modifier.weight(1f),
                    hasBackground = true,
                    backgroundColor = mediumGreen,
                    onClick = { if (userInfo != null) showEditDialog = true }
                )
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_rounded_refresh,
                    text = stringResource(R.string.profile_refresh_short),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val fresh = RetrofitInstance.userService.getUserInfo()
                                userInfo = fresh
                                error = null
                                CurrentUserManager.setUserId(context, fresh.id)
                                CurrentUserManager.setUserAdmin(context, fresh.isAdmin)
                                CurrentUserManager.setProfile(context, fresh)
                                showSnackbar(
                                    message = context.getString(R.string.profile_refresh_success),
                                    type = SnackbarType.SUCCESS,
                                    duration = SnackbarDuration.Short
                                )
                            } catch (e: Exception) {
                                error = e.localizedMessage
                            }
                        }
                    }
                )
            }

            // ⚠️ Danger zone
            TitledDivider(stringResource(R.string.profile_delete_danger_zone))
            ActionIconWithTextButton(
                iconRes = R.drawable.ic_rounded_delete_forever,
                text = stringResource(R.string.profile_delete_account),
                modifier = Modifier.fillMaxWidth(),
                hasBackground = true,
                backgroundColor = redMedium,
                onClick = {
                    deletePassword = ""
                    viewModel.resetDeleteState()
                    showDeleteDialog = true
                }
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    // ✏️ Dialog d'édition du profil
    if (showEditDialog && info != null) {
        EditProfileDialog(
            user = info,
            onDismiss = { showEditDialog = false },
            onSave = { req ->
                viewModel.updateProfile(
                    req = req,
                    onSuccess = { updated ->
                        // PATCH /me/profile renvoie maintenant UserOut AVEC le vrai
                        // email (2026-06-06) → on prend la réponse telle quelle.
                        userInfo = updated
                        CurrentUserManager.setProfile(context, updated)
                        showEditDialog = false
                        showSnackbar(
                            message = context.getString(R.string.profile_update_success),
                            type = SnackbarType.SUCCESS,
                            duration = SnackbarDuration.Short
                        )
                    },
                    onError = {
                        showEditDialog = false
                        showSnackbar(
                            message = context.getString(R.string.profile_update_error),
                            type = SnackbarType.ERROR,
                            duration = SnackbarDuration.Short
                        )
                    }
                )
            }
        )
    }

    // 🗑️ Dialog de suppression de compte (inchangé)
    if (showDeleteDialog) {
        val isDeleting = deleteState is ProfileScreenViewModel.DeleteAccountState.InProgress

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            containerColor = appColors.bgScreen,
            title = {
                Text(
                    stringResource(R.string.profile_delete_dialog_title),
                    color = appColors.primaryAction
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.profile_delete_dialog_body),
                        color = appColors.textPrimary
                    )
                    CustomTextField(
                        value = deletePassword,
                        onValueChange = {
                            deletePassword = it
                            if (deleteState is ProfileScreenViewModel.DeleteAccountState.Error) {
                                viewModel.resetDeleteState()
                            }
                        },
                        placeholder = stringResource(R.string.auth_password_placeholder),
                        label = stringResource(R.string.profile_delete_password_label),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    val state = deleteState
                    if (state is ProfileScreenViewModel.DeleteAccountState.Error) {
                        Text(
                            text = stringResource(state.messageRes),
                            color = redMedium,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                DialogPrimaryButton(
                    text = stringResource(R.string.common_delete),
                    color = redMedium,
                    enabled = deletePassword.isNotBlank() && !isDeleting,
                    onClick = {
                        viewModel.deleteAccount(deletePassword) {
                            showSnackbar(
                                message = context.getString(R.string.profile_delete_success),
                                type = SnackbarType.SUCCESS,
                                duration = SnackbarDuration.Short
                            )
                            navController.navigate(Routes.LOGOUT) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            },
            dismissButton = {
                DialogSecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                )
            }
        )
    }
}

/** Carte de section du profil : regroupe ses [DetailRow] dans un bloc bgRecessed
 *  arrondi (même langage visuel que la carte identité). */
@Composable
private fun ProfileSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.bgRecessed)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** "175.0" -> "175" ; "72.5" -> "72.5". */
private fun trimNum(f: Float): String =
    if (f % 1f == 0f) f.toInt().toString() else f.toString()
