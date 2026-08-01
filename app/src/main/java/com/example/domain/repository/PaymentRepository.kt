package com.example.domain.repository

import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.Result
import com.example.domain.model.Payment
import kotlinx.coroutines.flow.Flow

/** Payment repository contract. */
interface PaymentRepository {
    fun observe(): Flow<List<Payment>>
    fun observeByParent(parentId: String): Flow<List<Payment>>
    fun observeByStudent(studentId: String): Flow<List<Payment>>
    fun observeById(id: String): Flow<Payment?>
    suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment>
    suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment>
    suspend fun adjust(input: AdjustAccountInput, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [PaymentRepository.collect]. */
data class CollectPaymentInput(
    val parentId: String, val studentId: String?, val amount: Long,
    val method: PaymentMethod, val category: PaymentCategory,
    val installmentId: String? = null, val notes: String? = null,
    val checkNumber: String? = null, val checkBankName: String? = null,
    val checkIssueDate: String? = null, val checkClearanceDate: String? = null,
    val transferReference: String? = null, val transferSourceBank: String? = null,
    val proofPath: String? = null,
)

/** Input payload for [PaymentRepository.adjust]. */
data class AdjustAccountInput(
    val parentId: String, val studentId: String?,
    val category: PaymentCategory, val amount: Long,
    val reason: String, val receiptRef: String? = null,
)
