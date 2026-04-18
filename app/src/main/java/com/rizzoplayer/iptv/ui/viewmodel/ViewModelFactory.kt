package com.rizzoplayer.iptv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.repository.IPTVRepository
import com.rizzoplayer.iptv.data.repository.TorBoxRepository

class ViewModelFactory(
    private val repository: IPTVRepository,
    private val tmdbRepository: com.rizzoplayer.iptv.data.repository.TmdbRepository,
    private val torBoxRepository: TorBoxRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) -> LoginViewModel(repository, serversStore) as T
        modelClass.isAssignableFrom(MainViewModel::class.java)  -> MainViewModel(repository, tmdbRepository, torBoxRepository, serversStore, preferencesStore, application) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
