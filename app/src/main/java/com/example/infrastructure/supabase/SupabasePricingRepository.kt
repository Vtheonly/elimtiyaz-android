package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.PricingConfig
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.PricingRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of PricingRepository.
 *
 * Tables:
 *   - `pricing_configs`         — one active row per tenant per academic year
 *   - `grade_level_tuition`     — 14 grade levels with 3-tranche schedule
 *
 * `observe()` reads the `active_pricing_config` view (migration 0006) which
 * joins pricing_configs + academic_years WHERE `is_active = true AND ay.is_current = true`.
 *
 * Update methods resolve the active pricing_config_id server-side (RLS
 * enforces tenant isolation), then UPDATE / UPSERT the row.
 *
 * `updateTuitionForGradeLevel` resolves `gradeLevel` (grade_code) to
 * `academic_level_id` via the academic_levels table, then upserts the
 * grade_level_tuition row.
 *
 * Audit action: `AuditActions.PRICING_UPDATE` (`pricing.update`).
 */
@Singleton
class SupabasePricingRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : PricingRepository {

    override fun observe() = flow {
        emit(try {
            provider.postgrest.from("active_pricing_config")
                .select { limit(1) }
                .decodeList<PricingConfigDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override fun observeGradeLevelTuition() = flow {
        emit(try {
            // Fetch tuition rows + academic_levels separately, join client-side.
            val tuitionRows = provider.postgrest.from("grade_level_tuition")
                .select {
                    order("academic_level_id", Order.ASCENDING)
                    limit(50)
                }
                .decodeList<GradeLevelTuitionDto>()
            val levelIds = tuitionRows.map { it.academicLevelId }.distinct()
            val levels = if (levelIds.isEmpty()) emptyList<AcademicLevelDto>() else try {
                provider.postgrest.from("academic_levels")
                    .select { limit(100) }
                    .decodeList<AcademicLevelDto>()
            } catch (e: Exception) { emptyList() }
            val levelById = levels.associateBy { it.id }
            tuitionRows.map { dto ->
                dto.copy(academicLevels = levelById[dto.academicLevelId]).toDomain()
            }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit> = try {
        require(amount >= 0) { "Registration fee must be >= 0" }
        val configId = resolveActiveConfigId()
            ?: return Result.Err(Errors.notFound("No active pricing_config for current tenant"))
        provider.postgrest.from("pricing_configs").update(mapOf("registration_fee" to amount.toString())) {
            filter { eq("id", configId) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.PRICING_UPDATE,
            entityType = "pricing_config",
            entityId = configId,
            afterJson = """{"registration_fee":$amount}""",
            note = "Registration fee updated from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit> = try {
        require(amount >= 0) { "Late penalty must be >= 0" }
        val configId = resolveActiveConfigId()
            ?: return Result.Err(Errors.notFound("No active pricing_config for current tenant"))
        provider.postgrest.from("pricing_configs").update(mapOf("late_penalty_per_day" to amount.toString())) {
            filter { eq("id", configId) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.PRICING_UPDATE,
            entityType = "pricing_config",
            entityId = configId,
            afterJson = """{"late_penalty_per_day":$amount}""",
            note = "Late penalty updated from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateTuitionForGradeLevel(
        gradeLevel: String, annualAmount: Long,
        tranches: Triple<Long, Long, Long>,
        actorId: String, actorName: String,
    ): Result<Unit> = try {
        require(gradeLevel.isNotBlank()) { "Grade level code is required" }
        require(annualAmount >= 0) { "Annual amount must be >= 0" }
        val sumTranches = tranches.first + tranches.second + tranches.third
        require(kotlin.math.abs(sumTranches - annualAmount) < 1L) {
            "Tranches ($sumTranches) must sum to annual amount ($annualAmount)"
        }
        val configId = resolveActiveConfigId()
            ?: return Result.Err(Errors.notFound("No active pricing_config for current tenant"))
        val academicLevelId = resolveAcademicLevelId(gradeLevel)
            ?: return Result.Err(Errors.notFound("academic_levels row not found for grade_code=$gradeLevel"))

        val dto = GradeLevelTuitionUpsertDto(
            pricingConfigId = configId,
            academicLevelId = academicLevelId,
            annualAmount = annualAmount,
            tranche1Amount = tranches.first,
            tranche2Amount = tranches.second,
            tranche3Amount = tranches.third,
        )
        provider.postgrest.from("grade_level_tuition").upsert(dto) {
            select()
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.PRICING_UPDATE,
            entityType = "grade_level_tuition",
            entityId = "$configId:$academicLevelId",
            afterJson = """{"grade_level":"$gradeLevel","annual_amount":$annualAmount,"tranches":[${tranches.first},${tranches.second},${tranches.third}]}""",
            note = "Grade level tuition updated from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun resolveActiveConfigId(): String? = try {
        provider.postgrest.from("active_pricing_config")
            .select { limit(1) }
            .decodeList<PricingConfigDto>()
            .firstOrNull()
            ?.id
    } catch (e: Exception) { null }

    private suspend fun resolveAcademicLevelId(gradeCode: String): String? = try {
        provider.postgrest.from("academic_levels")
            .select {
                filter { eq("grade_code", gradeCode) }
                limit(1)
            }
            .decodeList<AcademicLevelDto>()
            .firstOrNull()
            ?.id
    } catch (e: Exception) { null }

    @Serializable
    data class PricingConfigDto(
        val id: String,
        val tenantId: String,
        val label: String,
        val registrationFee: Double = 0.0,
        val latePenaltyPerDay: Double = 0.0,
        val secondApronFee: Double = 0.0,
        val isActive: Boolean = true,
        val updatedAt: String = "",
    ) {
        fun toDomain() = PricingConfig(
            id = id,
            tenantId = tenantId,
            isActive = isActive,
            registrationFee = registrationFee.toLong(),
            latePenaltyPerDay = latePenaltyPerDay.toLong(),
            secondApronFee = secondApronFee.toLong(),
            updatedAt = updatedAt,
        )
    }

    @Serializable
    data class GradeLevelTuitionDto(
        val id: String,
        val pricingConfigId: String,
        val academicLevelId: String,
        val annualAmount: Double = 0.0,
        val tranche1Amount: Double = 0.0,
        val tranche2Amount: Double = 0.0,
        val tranche3Amount: Double = 0.0,
        val academicLevels: AcademicLevelDto? = null,
    ) {
        fun toDomain() = GradeLevelTuition(
            id = id,
            pricingConfigId = pricingConfigId,
            gradeLevel = academicLevels?.gradeCode ?: academicLevelId,
            annualAmount = annualAmount.toLong(),
            tranche1 = tranche1Amount.toLong(),
            tranche2 = tranche2Amount.toLong(),
            tranche3 = tranche3Amount.toLong(),
        )
    }

    @Serializable
    data class AcademicLevelDto(
        val id: String,
        val gradeCode: String,
        val yearLabel: String? = null,
    )

    @Serializable
    data class GradeLevelTuitionUpsertDto(
        val pricingConfigId: String,
        val academicLevelId: String,
        val annualAmount: Long,
        val tranche1Amount: Long,
        val tranche2Amount: Long,
        val tranche3Amount: Long,
        val tranche1Month: Int = 9,
        val tranche2Month: Int = 12,
        val tranche3Month: Int = 3,
    )
}
