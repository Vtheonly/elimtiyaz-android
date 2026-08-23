package com.example.infrastructure.pdf

import android.content.Context
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.PdfRepository
import com.example.domain.repository.StudentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

/**
 * Room-backed [PdfRepository] — assembles the domain data for each document
 * (payment + parent + student + ledger breakdown, or parent + ledger summary
 * + entries) and delegates the actual Canvas rendering to [PdfGenerator].
 *
 * Files are written to `{cacheDir}/pdf/` and returned for sharing via
 * FileProvider (see res/xml/file_paths.xml + AndroidManifest.xml).
 */
@Singleton
class AndroidPdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paymentRepository: PaymentRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val ledgerRepository: LedgerRepository,
) : PdfRepository {

    override suspend fun generatePaymentReceipt(paymentId: String): Result<File> {
        val payment = paymentRepository.observeById(paymentId).firstOrNull()
            ?: return Result.Err(Errors.notFound("Paiement $paymentId introuvable"))
        val parent = parentRepository.observeById(payment.parentId).firstOrNull()
        val student = payment.studentId?.let { sid ->
            studentRepository.observeById(sid).firstOrNull()
        }
        // Breakdown = every ledger entry whose sourceId is this payment
        // (the payment entry itself + any parent-credit overflow entry).
        val breakdown = ledgerRepository.observeByParent(payment.parentId)
            .firstOrNull()
            .orEmpty()
            .filter { it.sourceId == payment.id }
        return try {
            val file = PdfGenerator.generatePaymentReceipt(
                payment = payment,
                parent = parent,
                student = student,
                breakdown = breakdown,
                outputDir = pdfDir(),
            )
            Result.Ok(file)
        } catch (e: Exception) {
            Result.Err(Errors.unknown(
                "Payment receipt PDF failed: ${e.message ?: e::class.simpleName}",
                userMessage = "Échec de génération du reçu PDF.",
            ))
        }
    }

    override suspend fun generateAccountStatement(parentId: String): Result<File> {
        val parent = parentRepository.observeById(parentId).firstOrNull()
            ?: return Result.Err(Errors.notFound("Parent $parentId introuvable"))
        val summary = when (val r = ledgerRepository.summary(parentId)) {
            is Result.Ok -> r.value
            is Result.Err -> return r
        }
        val entries = ledgerRepository.observeByParent(parentId).firstOrNull().orEmpty()
        return try {
            val file = PdfGenerator.generateAccountStatement(
                parent = parent,
                summary = summary,
                entries = entries,
                outputDir = pdfDir(),
            )
            Result.Ok(file)
        } catch (e: Exception) {
            Result.Err(Errors.unknown(
                "Account statement PDF failed: ${e.message ?: e::class.simpleName}",
                userMessage = "Échec de génération du relevé PDF.",
            ))
        }
    }

    private fun pdfDir(): File = File(context.cacheDir, "pdf")
}
