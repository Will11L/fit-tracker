package com.example.sportapp.app

import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.sportapp.app.navigation.NavModeManager
import com.example.sportapp.core.data.repository.StorageManager
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.network.TokenManager
import com.example.sportapp.core.utils.AppConfig
import dagger.hilt.android.HiltAndroidApp
import com.example.sportapp.core.utils.NetworkMonitor
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.sportapp.core.data.remote.WebSocketManager


@HiltAndroidApp
class SportApp : Application(), Configuration.Provider {

    @Inject lateinit var wsManager: WebSocketManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    private lateinit var monitor: NetworkMonitor

    // Phase 3 (2026-05-12) : WorkManager init avec HiltWorkerFactory pour
    // que les Workers (ex. TaskReminderWorker) puissent etre injectes.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        AndroidThreeTen.init(this)
        CurrentUserManager.init(applicationContext)
        NavModeManager.init(applicationContext)
        StorageManager.init(this)
        StorageManager.initUserMuscleStorage()
        TokenManager.init(this)

        monitor = NetworkMonitor(this) {}
        monitor.start()

        // Doit etre appele AVANT RetrofitInstance.initialize : peuple les URLs
        // effectives depuis le DataStore "server_url_settings" (debug only, switcher
        // Settings -> Server URL). En release : no-op, garde BuildConfig defaults.
        AppConfig.init(this)
        RetrofitInstance.initialize(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        monitor.stop()
        wsManager.stop()
    }
}
