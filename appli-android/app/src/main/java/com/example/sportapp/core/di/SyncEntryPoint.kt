package com.example.sportapp.core.di

import com.example.sportapp.core.data.remote.WebSocketManager
import com.example.sportapp.core.sync.RemoteDataMerger
import com.example.sportapp.core.sync.SyncCoordinator
import com.example.sportapp.core.sync.SyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun remoteDataMerger(): RemoteDataMerger
    fun syncManager(): SyncManager
    fun syncCoordinator(): SyncCoordinator
    fun webSocketManager(): WebSocketManager
}
