package io.github.tukiza7_debug.vibecast.ui.importsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tukiza7_debug.vibecast.data.repo.ExtensionRepository
import io.github.tukiza7_debug.vibecast.importcode.ImportPromptBus
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds the visible confirmation state driven by deep links, shares and clipboard. */
class ImportPromptController(bus: ImportPromptBus) {
    data class Pending(
        val code: String,
        val origin: String,
        val details: ExtensionRepository.PendingImport?,
        val error: String?,
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** Single collecting scope for the bus; cancelled with [close] when the
     *  activity owning this controller goes away. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)

    init {
        scope.launch {
            bus.prompts.collect { prompt ->
                _pending.value = Pending(prompt.code, prompt.origin, details = null, error = null)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    fun offer(
        code: String,
        origin: String,
    ) {
        _pending.value = Pending(code, origin, details = null, error = null)
    }

    fun dismiss() {
        _pending.value = null
    }
}

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val repository: ExtensionRepository,
    ) : ViewModel() {
        fun resolve(
            code: String,
            onResolved: (ExtensionRepository.PendingImport) -> Unit,
            onError: (String) -> Unit,
        ) {
            viewModelScope.launch {
                runCatching { repository.codeDetails(code) }
                    .onSuccess(onResolved)
                    .onFailure { onError(it.message ?: "invalid code") }
            }
        }

        fun add(
            code: String,
            origin: String,
            onDone: (Result<ExtensionRepository.ImportResult>) -> Unit,
        ) {
            viewModelScope.launch {
                val result = runCatching { repository.importByCode(code, origin) }
                onDone(result)
            }
        }
    }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportSheetHost(controller: ImportPromptController) {
    val pending by controller.pending.collectAsState()
    val viewModel: ImportViewModel = hiltViewModel()
    val current = pending ?: return
    var details by remember(current.code) { mutableStateOf<ExtensionRepository.PendingImport?>(null) }
    var error by remember(current.code) { mutableStateOf<String?>(null) }
    var importing by remember(current.code) { mutableStateOf(false) }

    LaunchedEffect(current.code) {
        if (current.details == null && error == null) {
            viewModel.resolve(
                current.code,
                onResolved = { details = it },
                onError = { error = it },
            )
        }
    }

    ModalBottomSheet(onDismissRequest = { controller.dismiss() }) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add extension", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Source of this code: ${current.origin}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                details != null -> {
                    val d = details!!
                    Text(d.name, style = MaterialTheme.typography.titleLarge)
                    Text("id: ${d.id}  version: ${d.version}")
                    Text("Will contact: ${d.domains.joinToString()}")
                }
                error != null ->
                    Text(
                        "This code is not usable: $error",
                        color = MaterialTheme.colorScheme.error,
                    )
                else -> CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = details != null && !importing,
                    onClick = {
                        importing = true
                        viewModel.add(current.code, current.origin) { result ->
                            importing = false
                            controller.dismiss()
                            if (result.isFailure) {
                                error = result.exceptionOrNull()?.message
                            }
                        }
                    },
                ) { Text(if (importing) "Adding…" else "Add") }
                OutlinedButton(onClick = { controller.dismiss() }) { Text("Cancel") }
            }
        }
    }
}
