package com.example.sportapp.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.ServerUrlDataStore
import com.example.sportapp.core.data.ServerUrlPreset
import com.example.sportapp.core.data.ServerUrlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel de la section "Server URL" (admin users uniquement, gated par
 * `isAdmin` cote UI -- cf. SettingsScreen).
 *
 * Expose le snapshot DataStore (preset + customUrl), permet de modifier les
 * 2 (persistance live), et fournit un "Test connection" qui ping
 * `<api_base>healthz` avec un timeout court de 5s.
 *
 * Note : aucun changement ne prend effet pour Retrofit/WS sans restart de
 * l'app -- les URLs sont capturees au boot dans [com.example.sportapp.core.utils.AppConfig].
 * L'ecran affiche un dialog "Restart required" apres Apply (cf. UI).
 */
@HiltViewModel
class ServerUrlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ServerUrlRepository,
) : ViewModel() {

    val snapshot: StateFlow<ServerUrlDataStore.Snapshot> = repo.snapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerUrlDataStore.Snapshot(ServerUrlPreset.PI_PROD, ""),
    )

    sealed class TestResult {
        object Idle : TestResult()
        object Running : TestResult()
        data class Success(val httpStatus: Int) : TestResult()
        data class Failure(val message: String) : TestResult()
    }

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult

    fun setPreset(preset: ServerUrlPreset) = viewModelScope.launch {
        repo.setPreset(preset)
        _testResult.value = TestResult.Idle
    }

    fun setCustomUrl(url: String) = viewModelScope.launch {
        repo.setCustomUrl(url)
        _testResult.value = TestResult.Idle
    }

    /**
     * Ping `<api_base>healthz` -- endpoint utility hors prefixe /api/v1 cote
     * serveur (cf. CLAUDE.md "Endpoints utility hors prefixe : /healthz").
     * Construit l'URL effective depuis le snapshot courant (sans toucher
     * AppConfig, qui reflete encore l'URL au boot).
     */
    fun testConnection() {
        viewModelScope.launch {
            _testResult.value = TestResult.Running
            val snap = repo.current()
            val resolved = repo.resolveUrls(snap)
            // resolved.api se termine par "/api/v1/" -> on remonte au host :
            // strip "/api/v1/" pour pointer "/healthz" au root.
            val healthUrl = resolved.api.removeSuffix("/api/v1/").trimEnd('/') + "/healthz"

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(healthUrl).get().build()
            _testResult.value = try {
                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) TestResult.Success(response.code)
                        else TestResult.Failure("HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                TestResult.Failure(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
