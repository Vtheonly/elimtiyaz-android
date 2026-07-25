package com.elimtiyaz.feature.financials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.Receipt
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PaymentDetailViewModel — powers Route.PaymentDetail.
 *
 * Loads a single payment by id (read from [SavedStateHandle]) and exposes
 * a refund action gated by [Permission.RefundPayment]. The screen also
 * optionally generates a receipt if one isn't yet linked.
 */
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val paymentId: String = savedStateHandle.get<String>("paymentId").orEmpty()

    private val _state = MutableStateFlow(PaymentDetailUiState())
    val state: StateFlow<PaymentDetailUiState> = _state.asStateFlow()

    init { reload() }

    /** Re-fetch the payment. */
    fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            payments.payment(paymentId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(isLoading = false, payment = result.data) }
                    is Result.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Refund the payment. UI gates this on [Permission.RefundPayment].
     * Returns true on success.
     */
    fun refund(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = payments.refund(paymentId)) {
                is Result.Success -> {
                    _state.update { it.copy(payment = r.data) }
                    onResult(true, null)
                }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }

    /**
     * Generate (or fetch) the receipt for this payment. UI gates this on
     * [Permission.GenerateReceipt].
     */
    fun generateReceipt(onResult: (Boolean, String?) -> Unit) {
        val actorId = session.value?.userId.orEmpty()
        viewModelScope.launch {
            when (val r = payments.generateReceipt(paymentId, actorId)) {
                is Result.Success -> {
                    _state.update { it.copy(receipt = r.data) }
                    onResult(true, null)
                }
                is Result.Failure -> onResult(false, r.error.userMessage)
            }
        }
    }
}

/** Payment detail screen state. */
data class PaymentDetailUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val payment: Payment? = null,
    val receipt: Receipt? = null,
) {
    /** True when a refund can be issued (not already refunded/cancelled). */
    val canRefund: Boolean get() = payment?.let { it.status != "refunded" && it.status != "cancelled" } ?: false
}
