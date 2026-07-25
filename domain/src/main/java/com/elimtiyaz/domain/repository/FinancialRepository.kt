package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.CreatePaymentInput
import com.elimtiyaz.domain.model.DebtSummary
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.ParentFinancialProfile
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun payments(): Flow<Result<List<Payment>>>
    fun paymentsByParent(parentId: String): Flow<Result<List<Payment>>>
    fun paymentsByStudent(studentId: String): Flow<Result<List<Payment>>>
    fun payment(id: String): Flow<Result<Payment>>
    suspend fun collect(input: CreatePaymentInput, collectedBy: String): Result<Payment>
    suspend fun refund(id: String): Result<Payment>
    suspend fun adjust(parentId: String, amount: Double, reason: String, approvedBy: String): Result<AccountAdjustment>
    suspend fun generateReceipt(paymentId: String, generatedBy: String): Result<Receipt>
}

interface InstallmentRepository {
    fun installmentsByParent(parentId: String): Flow<Result<List<Installment>>>
    fun installmentsByStudent(studentId: String): Flow<Result<List<Installment>>>
    suspend fun createInstallment(parentId: String, studentId: String, category: PaymentCategory, label: String, amountDue: Double, dueDate: String): Result<Installment>
    suspend fun markPaid(id: String, paymentId: String): Result<Installment>
}

interface DebtRepository {
    fun debtSummary(): Flow<Result<List<DebtSummary>>>
    fun parentFinancialProfile(parentId: String): Flow<Result<ParentFinancialProfile>>
    suspend fun sendReminder(parentId: String): Result<Unit>
}
