package com.example.sportapp.feature.notifications.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.*
import androidx.navigation.NavController
import com.example.sportapp.feature.notifications.domain.NotificationNavigationMapper
import com.example.sportapp.feature.notifications.ui.components.EmptyNotificationsState
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.feature.notifications.ui.components.NotificationsSummaryRow
import com.example.sportapp.feature.notifications.ui.components.SwipeableNotificationItem

@Composable
fun NotificationsScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    var showReadAllConfirm by remember { mutableStateOf(false) }


    // Masque les pendingDeletion (tu peux aussi le faire au niveau DAO)
    val visibleNotifications = remember(notifications) {
        notifications.filter { !it.pendingDeletion }
    }

    var pendingDelete by remember { mutableStateOf<Notification?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen)
    ) {
        ScreenTitleBar(
            title = stringResource(R.string.drawer_item_notifications),
            onClick = { closeDrawer() } // ou drawer open/close, ou back, etc.
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(6 .dp))

            NotificationsSummaryRow(
                modifier = Modifier.fillMaxWidth(),
                unreadCount = unreadCount,
                totalCount = visibleNotifications.size,
                unsyncedCount = unsyncedCount,
                onReadAll = {
                    if (unreadCount > 0) {
                        showReadAllConfirm = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TitledDivider(title = stringResource(R.string.notifications_inbox))

            Spacer(modifier = Modifier.height(10.dp))

            if (visibleNotifications.isEmpty()) {
                EmptyNotificationsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(visibleNotifications, key = { it.uuid }) { notif ->
                        SwipeableNotificationItem(
                            notif = notif,
                            onClick = { viewModel.markAsRead(notif.uuid) },
                            onRequestDelete = { pendingDelete = notif },
                            onDeleteNow = { viewModel.markAsPendingDeletion(notif.uuid) },
                            onNavigate = { n ->
                                val target = NotificationNavigationMapper.resolve(n)
                                if (target != null) {
                                    if (target.markAsReadBeforeNavigate) {
                                        viewModel.markAsRead(n.uuid)
                                    }
                                    navController.navigate(target.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }

                        )
                    }
                }
            }
        }
    }

    fun closeReadAllConfirm() { showReadAllConfirm = false}
    showReadAllConfirm.takeIf { it }?.let {
        ConfirmationDialog(
            title = stringResource(R.string.notifications_read_all_title),
            message = stringResource(R.string.notifications_read_all_message),
            confirmButtonText = stringResource(R.string.notifications_read_all_button),
            confirmButtonColor = mediumGreen,
            onConfirm = {
                viewModel.markAllAsRead()
                closeReadAllConfirm()
            },
            onDismiss = { closeReadAllConfirm() }
        )
    }


    // Confirmation Delete
    fun closeConfirmationDialog() { pendingDelete = null}
    pendingDelete?.let { notif ->
        ConfirmationDialog(
            title = stringResource(R.string.notifications_delete_title),
            message = stringResource(R.string.notifications_delete_message),
            confirmButtonText = stringResource(R.string.common_delete),
            confirmButtonColor = redMedium,
            onConfirm = {
                viewModel.markAsPendingDeletion(notif.uuid)
                closeConfirmationDialog()
            },
            onDismiss = { closeConfirmationDialog() }
        )
    }
}
