package dev.openpolaris.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import dev.openpolaris.ui.AppViewModel

/**
 * Activity-retained owner for the shared OpenPolaris session.
 *
 * The Compose remember boundary only survives recomposition. This Android
 * ViewModel survives configuration changes, so rotating the phone does not
 * replace a live MountSession, motion owner, capture lifecycle or preference
 * callback. The shared VM still owns all protocol behavior; this class only
 * supplies the Android retention/lifecycle boundary.
 */
class OpenPolarisHostViewModel(
    sessionStore: SessionStore,
) : ViewModel() {
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val appViewModel: AppViewModel = AppViewModel(
        scope = ownerScope,
        connectionFactory = { JvmConnection() },
        sessionStore = sessionStore,
    )

    override fun onCleared() {
        appViewModel.disconnect()
        ownerScope.cancel()
        super.onCleared()
    }
}

class OpenPolarisHostViewModelFactory(
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(OpenPolarisHostViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return OpenPolarisHostViewModel(sessionStore) as T
    }
}
