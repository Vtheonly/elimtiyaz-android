package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.supabase.NetworkTimeouts
import com.example.infrastructure.supabase.ParentDto
import com.example.infrastructure.supabase.StudentDto
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.supabase.toEntity
import com.example.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull-side sync — fetches the LATEST parents + students (and, in future
 * revisions, payments + ledger entries + device tokens) from Supabase and
 * upserts them into the local Room cache.
 *
 * This is the FIX for the previous "push-only" sync architecture: the
 * Android app could write to Supabase via the `upsert_*_from_import` RPCs,
 * but never READ back what the Desktop app imported. The shared schema
 * (migration 0027 + 0028) defines `pull_parents_for_sync` /
 * `pull_students_for_sync` / `pull_payments_for_sync` /
 * `pull_ledger_entries_for_sync` / `pull_device_tokens_for_sync` RPCs that
 * return all rows for the current tenant (optionally filtered by
 * `p_since` for incremental sync).
 *
 * Flow:
 *   Desktop imports Excel → upsert_parent_from_import / upsert_student_from_import
 *   ↓
 *   Supabase stores the canonical rows
 *   ↓
 *   Android calls pull_parents_for_sync / pull_students_for_sync
 *   ↓
 *   Rows decoded as ParentDto / StudentDto
 *   ↓
 *   Room cache upserts via ParentEntity / StudentEntity
 *   ↓
 *   UI recomposes with the freshly-pulled data
 *
 * Idempotency: each pulled row is upserted by primary key (`id`) into Room,
 * so re-pulling the same data doesn't create duplicates. The RPCs themselves
 * are SECURITY DEFINER + read-only, so they're safe to call from the anon-key
 * client.
 *
 * Error handling: every step is wrapped in runCatching + logged. A network
 * failure or a Supabase error never crashes the caller — it just returns
 * `Result.Err` and the SyncWorker will retry on the next cycle.
 */
@Singleton
class PullSyncRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
    private val provider: SupabaseClientProvider,
    private val sessionManager: SessionManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Pull ALL parents from Supabase and upsert them into Room.
     * Tries the RPC first, falling back to direct table select if RPC is unavailable.
     */
    suspend fun pullParents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) {
            return@withContext Result.Err(com.example.core.Errors.server("Supabase non configuré — veuillez saisir votre URL et clé API Supabase dans les paramètres."))
        }
        val tenantId = sessionManager.currentTenantId() ?: "ten-elimtiyaz-001"
        try {
            var count = 0
            // 1. Try RPC first
            var fetched = false
            try {
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val raw = provider.postgrest.rpc("pull_parents_for_sync", params)
                val dtoList = json.decodeFromString(ListSerializer(ParentDto.serializer()), raw.toString())
                for (dto in dtoList) {
                    db.parentDao().upsert(dto.toEntity())
                    count++
                }
                fetched = true
            } catch (rpcEx: Throwable) {
                Log.d("PullSync", "RPC pull_parents_for_sync failed (${rpcEx.message}), attempting table query")
            }

            // 2. Direct select fallback if RPC did not populate
            if (!fetched || count == 0) {
                val tablesToTry = listOf("parents", "guardians", "tuteurs")
                for (table in tablesToTry) {
                    try {
                        val elements = provider.postgrest.from(table).select { limit(2000) }.decodeList<kotlinx.serialization.json.JsonObject>()
                        if (elements.isNotEmpty()) {
                            for (item in elements) {
                                try {
                                    val dto = json.decodeFromJsonElement(ParentDto.serializer(), item)
                                    if (dto.id.isNotBlank()) {
                                        db.parentDao().upsert(dto.toEntity())
                                        count++
                                        continue
                                    }
                                } catch (_: Throwable) {}
                                
                                val id = item["id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().removeSurrounding("\"") } ?: ""
                                if (id.isBlank()) continue
                                val firstName = item["first_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["prenom"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val lastName = item["last_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["nom"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val displayName = item["display_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "$firstName $lastName".trim()
                                val phone = item["primary_phone"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["phone"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["telephone"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val code = item["parent_code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "PAR-$id"

                                db.parentDao().upsert(
                                    ParentEntity(
                                        id = id,
                                        tenantId = item["tenant_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "ten-elimtiyaz-001" } ?: "ten-elimtiyaz-001",
                                        code = code,
                                        firstName = firstName.ifBlank { displayName.ifBlank { "Parent" } },
                                        lastName = lastName,
                                        displayName = displayName.ifBlank { null },
                                        phone = phone,
                                        whatsapp = item["secondary_phone"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        email = item["email"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        occupation = item["occupation"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        address = item["address"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        transportDestination = item["transport_destination"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        preferredLanguage = "fr",
                                        avatarUrl = null,
                                        isActive = true,
                                        isFinanciallyRestricted = false,
                                        activationCode = null,
                                        createdAt = item["created_at"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                        updatedAt = item["updated_at"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                    )
                                )
                                count++
                            }
                            if (count > 0) break
                        }
                    } catch (tEx: Throwable) {
                        Log.d("PullSync", "Table $table query failed: ${tEx.message}")
                    }
                }
            }

            Log.i("PullSync", "Pulled $count parents from Supabase")
            Result.Ok(count)
        } catch (e: Exception) {
            Log.w("PullSync", "pullParents failed: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL students from Supabase and upsert them into Room.
     * Tries the RPC first, falling back to direct table select if RPC is unavailable.
     */
    suspend fun pullStudents(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) {
            return@withContext Result.Err(com.example.core.Errors.server("Supabase non configuré — veuillez saisir votre URL et clé API Supabase dans les paramètres."))
        }
        val tenantId = sessionManager.currentTenantId() ?: "ten-elimtiyaz-001"
        try {
            var count = 0
            // 1. Try RPC first
            var fetched = false
            try {
                val params = buildJsonObject {
                    put("p_tenant_id", tenantId)
                    if (sinceIso != null) put("p_since", sinceIso)
                    put("p_limit", 2000)
                }
                val raw = provider.postgrest.rpc("pull_students_for_sync", params)
                val dtoList = json.decodeFromString(ListSerializer(StudentDto.serializer()), raw.toString())
                for (dto in dtoList) {
                    db.studentDao().upsert(dto.toEntity())
                    count++
                }
                fetched = true
            } catch (rpcEx: Throwable) {
                Log.d("PullSync", "RPC pull_students_for_sync failed (${rpcEx.message}), attempting table query")
            }

            // 2. Direct select fallback if RPC did not populate
            if (!fetched || count == 0) {
                val tablesToTry = listOf("students", "eleves", "etudiants")
                for (table in tablesToTry) {
                    try {
                        val elements = provider.postgrest.from(table).select { limit(2000) }.decodeList<kotlinx.serialization.json.JsonObject>()
                        if (elements.isNotEmpty()) {
                            for (item in elements) {
                                try {
                                    val dto = json.decodeFromJsonElement(StudentDto.serializer(), item)
                                    if (dto.id.isNotBlank()) {
                                        db.studentDao().upsert(dto.toEntity())
                                        count++
                                        continue
                                    }
                                } catch (_: Throwable) {}

                                val id = item["id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().removeSurrounding("\"") } ?: ""
                                if (id.isBlank()) continue
                                val firstName = item["first_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["prenom"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val lastName = item["last_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["nom"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val displayName = item["display_name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["name"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "$firstName $lastName".trim()
                                val code = item["student_code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["matricule"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "ELV-$id"
                                val parentId = item["parent_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["guardian_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["tuteur_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                                val gradeCode = item["grade_level_code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["grade_code"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["level"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["niveau"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "1ap"
                                val status = item["enrollment_status"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "active" } ?: item["status"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "active" } ?: "active"

                                db.studentDao().upsert(
                                    StudentEntity(
                                        id = id,
                                        tenantId = item["tenant_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "ten-elimtiyaz-001" } ?: "ten-elimtiyaz-001",
                                        code = code,
                                        parentId = parentId,
                                        firstName = firstName.ifBlank { displayName.ifBlank { "Élève" } },
                                        lastName = lastName,
                                        displayName = displayName.ifBlank { null },
                                        gender = item["gender"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "unspecified" } ?: "unspecified",
                                        birthDate = item["date_of_birth"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: item["birth_date"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                        enrollmentDate = item["enrollment_date"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                        level = gradeCode,
                                        gradeLevel = gradeCode,
                                        classId = item["class_id"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        photoUrl = null,
                                        medicalNotes = item["medical_notes"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null },
                                        status = status,
                                        createdAt = item["created_at"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                        updatedAt = item["updated_at"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: "",
                                    )
                                )
                                count++
                            }
                            if (count > 0) break
                        }
                    } catch (tEx: Throwable) {
                        Log.d("PullSync", "Table $table query failed: ${tEx.message}")
                    }
                }
            }

            Log.i("PullSync", "Pulled $count students from Supabase")
            Result.Ok(count)
        } catch (e: Exception) {
            Log.w("PullSync", "pullStudents failed: ${e.message}", e)
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL payments from Supabase and upsert them into Room.
     */
    suspend fun pullPayments(): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) {
            return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        }
        try {
            val dtoList = provider.postgrest.from("payments")
                .select { limit(2000) }
                .decodeList<com.example.infrastructure.supabase.PaymentDto>()

            for (dto in dtoList) {
                val entity = com.example.infrastructure.room.PaymentEntity(
                    id = dto.id,
                    tenantId = dto.tenantId ?: "ten-elimtiyaz-001",
                    receiptNumber = dto.receiptNumber ?: dto.paymentNumber,
                    parentId = dto.parentId,
                    studentId = dto.studentId,
                    amount = (dto.amount * 100).toLong(),
                    method = dto.method.lowercase(),
                    status = dto.status.lowercase(),
                    category = dto.category ?: "tuition",
                    installmentId = dto.installmentId,
                    proofUrl = dto.proofPath,
                    checkNumber = null,
                    checkBankName = null,
                    checkIssueDate = null,
                    checkClearanceDate = null,
                    transferReference = null,
                    transferSourceBank = null,
                    notes = dto.notes,
                    collectedBy = dto.collectedBy ?: "system",
                    collectedBy_name = "Système",
                    collectedAt = dto.collectedAt ?: dto.createdAt ?: "",
                    createdAt = dto.createdAt ?: "",
                    updatedAt = dto.updatedAt ?: "",
                )
                db.paymentDao().upsert(entity)
            }
            Log.i("PullSync", "Pulled ${dtoList.size} payments from Supabase")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.d("PullSync", "pullPayments info: ${e.message}")
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL classes from Supabase and upsert them into Room.
     */
    suspend fun pullClasses(): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        try {
            val dtoList = provider.postgrest.from("classes").select { limit(2000) }.decodeList<com.example.infrastructure.supabase.ClassDto>()
            for (dto in dtoList) {
                db.academicClassDao().upsert(dto.toEntity())
            }
            Log.i("PullSync", "Pulled ${dtoList.size} classes from Supabase")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.d("PullSync", "pullClasses info: ${e.message}")
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL subjects from Supabase and upsert them into Room.
     */
    suspend fun pullSubjects(): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        try {
            val dtoList = provider.postgrest.from("subjects").select { limit(2000) }.decodeList<com.example.infrastructure.supabase.SubjectDto>()
            for (dto in dtoList) {
                db.subjectDao().upsert(dto.toEntity())
            }
            Log.i("PullSync", "Pulled ${dtoList.size} subjects from Supabase")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.d("PullSync", "pullSubjects info: ${e.message}")
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Pull ALL installments from Supabase and upsert them into Room.
     */
    suspend fun pullInstallments(): Result<Int> = withContext(Dispatchers.IO) {
        if (!provider.isConfigured()) return@withContext Result.Err(com.example.core.Errors.server("Supabase not configured"))
        try {
            val dtoList = provider.postgrest.from("installments").select { limit(2000) }.decodeList<com.example.infrastructure.supabase.InstallmentDto>()
            for (dto in dtoList) {
                db.installmentDao().upsert(dto.toEntity())
            }
            Log.i("PullSync", "Pulled ${dtoList.size} installments from Supabase")
            Result.Ok(dtoList.size)
        } catch (e: Exception) {
            Log.d("PullSync", "pullInstallments info: ${e.message}")
            Result.Err(com.example.core.Errors.fromException(e))
        }
    }

    /**
     * Convenience: pull parents + students + payments + classes + subjects + installments in one call.
     */
    suspend fun pullAll(sinceIso: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val parents = pullParents(sinceIso)
        val students = pullStudents(sinceIso)
        val payments = pullPayments()
        val classes = pullClasses()
        val subjects = pullSubjects()
        val installments = pullInstallments()
        val p = (parents as? Result.Ok)?.value ?: 0
        val s = (students as? Result.Ok)?.value ?: 0
        val pay = (payments as? Result.Ok)?.value ?: 0
        val cls = (classes as? Result.Ok)?.value ?: 0
        val sub = (subjects as? Result.Ok)?.value ?: 0
        val ins = (installments as? Result.Ok)?.value ?: 0
        val total = p + s + pay + cls + sub + ins

        if (total == 0) {
            val firstErr = (students as? Result.Err)?.error
                ?: (parents as? Result.Err)?.error
                ?: (payments as? Result.Err)?.error
                ?: com.example.core.Errors.network("0 enregistrement reçu de Supabase. Vérifiez les droits RLS et tables.")
            return@withContext Result.Err(firstErr)
        }

        Log.i("PullSync", "pullAll complete: $total rows synchronized from Supabase")
        Result.Ok(total)
    }
}
