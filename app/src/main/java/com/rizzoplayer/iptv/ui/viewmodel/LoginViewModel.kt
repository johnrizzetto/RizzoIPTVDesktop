package com.rizzoplayer.iptv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.model.Credentials
import com.rizzoplayer.iptv.data.model.ServerConfig
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val repository: IPTVRepository,
    private val serversStore: ServersStore
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(url: String, username: String, password: String) {
        var normalizedUrl = url.trim()
        if (normalizedUrl.isNotBlank() && !normalizedUrl.startsWith("http")) {
            normalizedUrl = "http://$normalizedUrl"
        }
        val creds = Credentials(
            url = normalizedUrl,
            username = username.trim(),
            password = password.trim()
        )
        if (!creds.isValid()) {
            _state.value = LoginUiState.Error("Please fill in server URL, username, and password.")
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            val ok = repository.testConnection(creds)
            if (ok) {
                repository.credentialsStore.save(creds)
                // Auto-save server to the saved servers list
                val label = normalizedUrl
                    .removePrefix("http://").removePrefix("https://")
                    .substringBefore("/").substringBefore(":")
                serversStore.addOrUpdate(
                    ServerConfig(
                        label = label,
                        url = normalizedUrl,
                        username = username.trim(),
                        password = password.trim()
                    )
                )
                _state.value = LoginUiState.Success
            } else {
                _state.value = LoginUiState.Error("Could not connect. Check your server address and credentials.")
            }
        }
    }

    fun resetError() {
        if (_state.value is LoginUiState.Error) _state.value = LoginUiState.Idle
    }
}
