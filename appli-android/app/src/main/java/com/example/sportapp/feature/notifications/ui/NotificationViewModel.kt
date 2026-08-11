package com.example.sportapp.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.NotificationDao
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.data.NotificationRepository
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repo: NotificationRepository,

    private val notificationDao: NotificationDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    val notifications: StateFlow<List<Notification>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> =
        repo.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedCount: StateFlow<Int> =
        repo.observeUnsyncedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(uuid: String) {
        viewModelScope.launch {
            repo.markAsRead(uuid)
            syncEngine.pushEntityClass(Notification::class)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repo.markAllAsRead()
            syncEngine.pushEntityClass(Notification::class)
        }
    }

    fun markAsPendingDeletion(uuid: String) {
        viewModelScope.launch {
            repo.markAsPendingDeletion(uuid)
            syncEngine.pushEntityClass(Notification::class)
        }
    }

}
