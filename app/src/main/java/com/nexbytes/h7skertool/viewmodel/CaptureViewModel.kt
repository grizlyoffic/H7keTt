package com.nexbytes.h7skertool.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.model.LogEntry
import com.nexbytes.h7skertool.model.LogLevel
import com.nexbytes.h7skertool.service.ProxyForegroundService
import com.nexbytes.h7skertool.service.ShizukuFileService
import com.nexbytes.h7skertool.session.SessionManager
import com.nexbytes.h7skertool.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

data class AppUiState(
    val shizukuAvailable: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val isVerified: Boolean = false,
    val isVerifying: Boolean = false,
    val verifyError: String? = null,
    val clientUrl: String = "",
    val isCapturing: Boolean = false,
    val requests: List<CapturedRequest> = emptyList(),
    val responses: Map<String, CapturedResponse> = emptyMap(),
    val logs: List<LogEntry> = emptyList(),
    val savedMods: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val endpointFilter: String? = null,
    val fileWriteStatus: List<String> = emptyList(),
    val errorMessage: String? = null,
    val username: String = ""
) {
    val readyToCapture get() = shizukuAvailable && shizukuPermissionGranted && isVerified && clientUrl.isNotEmpty()
    val needsShizuku get() = !shizukuAvailable || !shizukuPermissionGranted
    val needsPassword get() = shizukuAvailable && shizukuPermissionGranted && !isVerified
    val needsClientUrl get() = shizukuAvailable && shizukuPermissionGranted && isVerified && clientUrl.isEmpty()

    val filteredRequests: List<CapturedRequest> get() {
        var list = requests
        if (searchQuery.isNotBlank())
            list = list.filter {
                it.endpoint.contains(searchQuery, true) ||
                it.url.contains(searchQuery, true) ||
                it.bodyText?.contains(searchQuery, true) == true
            }
        if (endpointFilter != null)
            list = list.filter { it.endpoint == endpointFilter }
        return list
    }

    val allEndpoints: List<String> get() = requests.map { it.endpoint }.distinct().sorted()
}

class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "CaptureViewModel"
    private val session = SessionManager(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(shizukuPermissionGranted = granted) }
        log(LogLevel.INFO, "Shizuku", if (granted) "Permission granted ✓" else "Permission denied ✗")
    }

    init {
        setupCallbacks()
        startShizukuPoller()
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            session.isVerified.collect { v -> _state.update { it.copy(isVerified = v) } }
        }
        viewModelScope.launch {
            session.clientUrl.collect { url -> _state.update { it.copy(clientUrl = url) } }
        }
        viewModelScope.launch {
            session.username.collect { u -> _state.update { it.copy(username = u) } }
        }
    }

    private fun setupCallbacks() {
        ProxyForegroundService.onCapture = { req, res ->
            _state.update { s ->
                s.copy(
                    requests = listOf(req) + s.requests,
                    responses = s.responses + (req.id to res)
                )
            }
        }
        ProxyForegroundService.onLog = { msg -> log(LogLevel.INFO, "Proxy", msg) }
    }

    private fun startShizukuPoller() {
        viewModelScope.launch {
            while (true) {
                val avail = ShizukuManager.isShizukuAvailable()
                val granted = if (avail) ShizukuManager.hasPermission() else false
                _state.update { it.copy(shizukuAvailable = avail, shizukuPermissionGranted = granted) }
                delay(2000)
            }
        }
    }

    fun checkShizuku() {
        val avail = ShizukuManager.isShizukuAvailable()
        val granted = if (avail) ShizukuManager.hasPermission() else false
        _state.update { it.copy(shizukuAvailable = avail, shizukuPermissionGranted = granted) }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission(permissionListener)
        log(LogLevel.INFO, "Shizuku", "Requesting permission...")
    }

    fun verifyPassword(password: String) {
        if (password.isBlank()) {
            _state.update { it.copy(verifyError = "Password cannot be empty") }
            return
        }
        _state.update { it.copy(isVerifying = true, verifyError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://gamev1-vercel.app/password=$password"
                val response = http.newCall(Request.Builder().url(url).get().build()).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Password API response: $body")

                if (body.contains("\"result\":\"0\"") || body.contains("\"result\": \"0\"")) {
                    session.setVerified(true, username = password)
                    _state.update { it.copy(isVerifying = false, verifyError = null) }
                    log(LogLevel.INFO, "Auth", "Verified successfully ✓")
                } else {
                    _state.update { it.copy(isVerifying = false, verifyError = "Invalid password. Access denied.") }
                    log(LogLevel.WARNING, "Auth", "Verification failed — response: $body")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isVerifying = false, verifyError = "Network error: ${e.message}") }
                log(LogLevel.ERROR, "Auth", "Verification error: ${e.message}")
            }
        }
    }

    fun setClientUrl(url: String) {
        viewModelScope.launch {
            val clean = url.trim().trimEnd('/')
            session.setClientUrl(clean)
            log(LogLevel.INFO, "Config", "Client URL set: $clean")
        }
    }

    fun startCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            log(LogLevel.INFO, "Capture", "Writing localconfig.json via Shizuku...")
            val results = ShizukuFileService.writeLocalConfigFiles()
            val statusLines = results.map { if (it.success) "✓ ${it.path}" else "✗ ${it.path}: ${it.error}" }
            _state.update { it.copy(fileWriteStatus = statusLines) }
            statusLines.forEach { log(LogLevel.INFO, "FileService", it) }

            log(LogLevel.INFO, "Proxy", "Starting proxy → ${_state.value.clientUrl}")
            ProxyForegroundService.savedMods = _state.value.savedMods
            ProxyForegroundService.start(getApplication(), _state.value.clientUrl)
            _state.update { it.copy(isCapturing = true) }
        }
    }

    fun stopCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            ProxyForegroundService.stop(getApplication())
            ShizukuFileService.removeLocalConfigFiles()
            _state.update { it.copy(isCapturing = false) }
            log(LogLevel.INFO, "Capture", "Capture stopped. localconfig.json removed.")
        }
    }

    fun clearCaptures() {
        _state.update { it.copy(requests = emptyList(), responses = emptyMap()) }
    }

    fun setSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun setEndpointFilter(ep: String?) { _state.update { it.copy(endpointFilter = ep) } }
    fun dismissError() { _state.update { it.copy(errorMessage = null) } }

    fun saveModification(endpoint: String, modifiedBody: String) {
        val mods = _state.value.savedMods.toMutableMap()
        mods[endpoint] = modifiedBody
        _state.update { it.copy(savedMods = mods) }
        ProxyForegroundService.savedMods = mods
        log(LogLevel.INFO, "Mod", "Saved modification for $endpoint")
    }

    fun clearModifications() {
        _state.update { it.copy(savedMods = emptyMap()) }
        ProxyForegroundService.savedMods = emptyMap()
    }

    fun logout() {
        viewModelScope.launch {
            if (_state.value.isCapturing) stopCapture()
            session.logout()
            _state.update { it.copy(isVerified = false, username = "") }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            if (_state.value.isCapturing) stopCapture()
            session.resetAll()
            _state.update { AppUiState() }
        }
    }

    private fun log(level: LogLevel, tag: String, msg: String) {
        _state.update { s -> s.copy(logs = s.logs + LogEntry(level = level, tag = tag, message = msg)) }
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuManager.removePermissionListener(permissionListener)
        ProxyForegroundService.onCapture = null
        ProxyForegroundService.onLog = null
    }
}
