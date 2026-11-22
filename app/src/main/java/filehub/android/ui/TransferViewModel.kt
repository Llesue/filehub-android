package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.data.transfer.TransferItem
import filehub.android.data.transfer.TransferRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferViewModel(
    private val repository: TransferRepository,
) : ViewModel() {

    val items: StateFlow<List<TransferItem>> = repository.items.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun cancel(item: TransferItem) {
        viewModelScope.launch { repository.cancel(item) }
    }

    fun retry(item: TransferItem) {
        viewModelScope.launch { repository.retry(item) }
    }

    fun remove(item: TransferItem) {
        viewModelScope.launch { repository.remove(item) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                TransferViewModel(app.container.transferRepository)
            }
        }
    }
}
