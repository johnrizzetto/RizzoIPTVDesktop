package com.rizzoplayer.iptv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rizzoplayer.iptv.data.local.PreferencesStore
import com.rizzoplayer.iptv.data.local.ServersStore
import com.rizzoplayer.iptv.data.repository.IPTVRepository

class ViewModelFactory(
    private val repository: IPTVRepository,
    private val serversStore: ServersStore,
    private val preferencesStore: PreferencesStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) -> LoginViewModel(repository, serversStore) as T
        modelClass.isAssignableFrom(MainViewModel::class.java)  -> MainViewModel(repository, serversStore, preferencesStore) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
