package com.example.ui.features.financials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for [InstallmentScheduleScreen].
 *
 * BUGFIX (iter 2): the previous version injected [InstallmentRepository]
 * then discarded it — the StateFlow emitted a permanent empty list and
 * there was no way to select a parent. Now the ViewModel:
 *   - Loads the parent list (so the user can pick one).
 *   - On parent selection, switches the installments flow to
 *     [InstallmentRepository.observeByParent].
 *   - Exposes the selected parent so the UI can show its name.
 */
@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    private val installmentRepository: InstallmentRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    val parents: StateFlow<List<Parent>> = parentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedParentId = MutableStateFlow<String?>(null)
    val selectedParentId: StateFlow<String?> = _selectedParentId.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun selectParent(parentId: String) {
        _selectedParentId.value = parentId
        viewModelScope.launch {
            installmentRepository.observeByParent(parentId).collect { _installments.value = it }
        }
    }

    /**
     * Mark an installment as paid. Mirrors desktop's "Mark Paid" action —
     * calls [InstallmentRepository.markPaid] which invokes the
     * `mark_installment_paid` SECURITY DEFINER RPC.
     */
    fun markPaid(installmentId: String, actorId: String, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = installmentRepository.markPaid(installmentId, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Tranche marquée comme payée." }
                .onFailure { _message.value = it.userMessage }
        }
    }

    fun clearMessage() { _message.value = null }
}
