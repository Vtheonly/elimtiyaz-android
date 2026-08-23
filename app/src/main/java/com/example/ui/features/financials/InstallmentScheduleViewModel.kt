package com.example.ui.features.financials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LedgerEngine
import com.example.core.ParentLedgerSummary
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.session.SessionManager
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
 *
 * TIER 4 FIX (bypass #2): added [ledgerRepository] + [parentSummary].
 * The Progression card previously used inline `installments.sumOf { amountDue }
 * / amountPaid`, which diverged from the canonical ledger when reversals /
 * adjustments / credits were present. The summary is now derived from
 * `LedgerEngine.computeParentSummary` (the canonical engine), collected
 * reactively so it stays in sync as the ledger changes.
 */
@HiltViewModel
class InstallmentScheduleViewModel @Inject constructor(
    private val installmentRepository: InstallmentRepository,
    private val parentRepository: ParentRepository,
    private val ledgerRepository: LedgerRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val parents: StateFlow<List<Parent>> = parentRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedParentId = MutableStateFlow<String?>(null)
    val selectedParentId: StateFlow<String?> = _selectedParentId.asStateFlow()

    private val _installments = MutableStateFlow<List<Installment>>(emptyList())
    val installments: StateFlow<List<Installment>> = _installments.asStateFlow()

    // TIER 4 FIX (bypass #2) — canonical parent-level summary.
    private val _parentSummary = MutableStateFlow<ParentLedgerSummary?>(null)
    val parentSummary: StateFlow<ParentLedgerSummary?> = _parentSummary.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun selectParent(parentId: String) {
        _selectedParentId.value = parentId
        // Reset the summary while the new one loads.
        _parentSummary.value = null
        viewModelScope.launch {
            installmentRepository.observeByParent(parentId).collect { _installments.value = it }
        }
        // TIER 4 FIX (bypass #2) — collect the canonical parent summary.
        viewModelScope.launch {
            ledgerRepository.observeByParent(parentId).collect { entries ->
                _parentSummary.value = LedgerEngine.computeParentSummary(entries, parentId, "")
            }
        }
    }

    /**
     * Mark an installment as paid. Mirrors desktop's "Mark Paid" action —
     * calls [InstallmentRepository.markPaid] which invokes the
     * `mark_installment_paid` SECURITY DEFINER RPC.
     *
     * FIX (actor mis-attribution): the screen previously passed the PARENT's
     * id/name as the audit actor — the audit trail blamed the parent for
     * their own payment. The actor is now the logged-in user.
     */
    fun markPaid(installmentId: String) {
        viewModelScope.launch {
            _busy.value = true
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            val result = installmentRepository.markPaid(installmentId, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Tranche marquée comme payée (paiement + écriture comptable enregistrés)." }
                .onFailure { _message.value = it.userMessage }
        }
    }

    fun clearMessage() { _message.value = null }
}
