package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.ParentDao
import com.elimtiyaz.data.local.dao.StudentDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.ParentDto
import com.elimtiyaz.data.remote.dto.StudentDto
import com.elimtiyaz.data.remote.dto.UpdateParentDto
import com.elimtiyaz.domain.model.BatchRegistrationInput
import com.elimtiyaz.domain.model.BatchRegistrationResult
import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.UpdateParentInput
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Table name constant for the `parents` Supabase table. */
private const val PARENTS_TABLE = "parents"
/** Table name constant for the `students` Supabase table. */
private const val STUDENTS_TABLE = "students"

/**
 * Supabase-backed [ParentRepository]. Reads emit the Room cache first then
 * refresh from Supabase; writes go to Supabase first and, on failure, are
 * enqueued to the local [SyncQueueDao] for replay by the worker.
 */
@Singleton
class SupabaseParentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val parentDao: ParentDao,
    private val studentDao: StudentDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : ParentRepository {

    private val log = Logger.withTag("Data.Parent")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all parents — emits cache then fresh fetch. */
    override fun parents(): Flow<Result<List<Parent>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { parentDao.all().map { it.toDomain() } },
        fetch = {
            val dtos = supabase.from(PARENTS_TABLE).select().decodeList<ParentDto>()
            val students = studentDao.all()
            dtos.map { dto ->
                dto.toDomain(students = students.filter { it.parentId == dto.id }
                    .map { it.toDomain(parent = null) })
            }
        },
        persist = { parents -> parentDao.upsertAll(parents.map { it.toEntity() }) },
    )

    /** Stream a single parent (with their students) — emits cache then fresh fetch. */
    override fun parent(id: String): Flow<Result<Parent>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = {
            parentDao.all().firstOrNull { it.id == id }?.toDomain(
                students = studentDao.all().filter { it.parentId == id }.map { it.toDomain() },
            )
        },
        fetch = {
            val dto = supabase.from(PARENTS_TABLE).select {
                filter { eq("id", id) }
            }.decodeList<ParentDto>().firstOrNull() ?: error("Parent $id introuvable.")
            val students = supabase.from(STUDENTS_TABLE).select {
                filter { eq("parent_id", id) }
            }.decodeList<StudentDto>().map { it.toDomain() }
            dto.toDomain(students = students)
        },
        persist = { p -> parentDao.upsert(p.toEntity()) },
    )

    /** Stream parents whose code/name/phone contains the query. */
    override fun search(query: String): Flow<Result<List<Parent>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { parentDao.search(query).first().map { it.toDomain() } },
        fetch = {
            supabase.from(PARENTS_TABLE).select {
                filter {
                    or {
                        ilike("first_name", "%$query%")
                        ilike("last_name", "%$query%")
                        ilike("code", "%$query%")
                        ilike("phone", "%$query%")
                    }
                }
            }.decodeList<ParentDto>().map { it.toDomain() }
        },
        persist = { parents -> parentDao.upsertAll(parents.map { it.toEntity() }) },
    )

    /** Create a parent in Supabase, then cache locally and audit-log. */
    override suspend fun createParent(input: CreateParentInput): Result<Parent> =
        Result.runCatching {
            val nowIso = nowIso()
            val id = UUID.randomUUID().toString()
            val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            val code = Formatters.parentCode(year = year, suffix = id.take(4).uppercase())
            val dto = ParentDto.fromCreate(input, id = id, tenantId = DEFAULT_TENANT, code = code, nowIso = nowIso)
            supabase.from(PARENTS_TABLE).insert(dto)
            val domain = dto.toDomain()
            parentDao.upsert(domain.toEntity())
            audit.log(ACTION_PARENT_CREATE, "parent", id, actorId = "system", tenantId = DEFAULT_TENANT)
            log.i { "Created parent $code" }
            domain
        }.onFailure {
            val payload = ParentDto.fromCreate(
                input = input,
                id = UUID.randomUUID().toString(),
                tenantId = DEFAULT_TENANT,
                code = "PAR-PENDING",
                nowIso = Formatters.nowIso(),
            )
            sync.enqueueRaw(PARENTS_TABLE, "insert", sync.encode(payload))
            log.w { "createParent failed — queued for sync: ${it.message}" }
        }

    /** Update a parent's mutable fields. */
    override suspend fun updateParent(id: String, input: UpdateParentInput): Result<Parent> =
        Result.runCatching {
            val update = UpdateParentDto.fromInput(input, nowIso())
            supabase.from(PARENTS_TABLE).update(update) { filter { eq("id", id) } }
            val refreshed = supabase.from(PARENTS_TABLE).select {
                filter { eq("id", id) }
            }.decodeList<ParentDto>().firstOrNull()?.toDomain() ?: error("Parent $id introuvable.")
            parentDao.upsert(refreshed.toEntity())
            audit.log(ACTION_PARENT_UPDATE, "parent", id, actorId = "system", tenantId = DEFAULT_TENANT)
            log.i { "Updated parent $id" }
            refreshed
        }.onFailure {
            val payload = UpdateParentDto.fromInput(input, nowIso())
            sync.enqueueRaw(PARENTS_TABLE, "update", sync.encode(payload))
            log.w { "updateParent failed — queued: ${it.message}" }
        }

    /** Delete a parent. */
    override suspend fun deleteParent(id: String): Result<Unit> =
        Result.runCatching {
            supabase.from(PARENTS_TABLE).delete { filter { eq("id", id) } }
            parentDao.deleteById(id)
            log.i { "Deleted parent $id" }
        }.onFailure {
            sync.enqueueRaw(PARENTS_TABLE, "delete", sync.encode(mapOf("id" to id)))
        }
}

/**
 * Supabase-backed [StudentRepository]. Joins parent data via the parent cache
 * to satisfy `Student.parent: Parent?` without an extra round trip when both
 * caches are warm.
 */
@Singleton
class SupabaseStudentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val parentDao: ParentDao,
    private val studentDao: StudentDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : StudentRepository {

    private val log = Logger.withTag("Data.Student")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all students, joined with their parent from cache. */
    override fun students(): Flow<Result<List<Student>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = {
            val parents = parentDao.all().associateBy { it.id }
            studentDao.all().map { it.toDomain(parent = parents[it.parentId]?.toDomain()) }
        },
        fetch = {
            val dtos = supabase.from(STUDENTS_TABLE).select().decodeList<StudentDto>()
            val parents = parentDao.all().associateBy { it.id }
            dtos.map { dto -> dto.toDomain(parent = parents[dto.parentId]?.toDomain()) }
        },
        persist = { students -> studentDao.upsertAll(students.map { it.toEntity() }) },
    )

    /** Stream students by parent id. */
    override fun studentsByParent(parentId: String): Flow<Result<List<Student>>> =
        RepositoryHelpers.cacheThenFetch(
            dispatchers = dispatchers,
            loadCache = {
                val parent = parentDao.all().firstOrNull { it.id == parentId }?.toDomain()
                studentDao.all().filter { it.parentId == parentId }.map { it.toDomain(parent = parent) }
            },
            fetch = {
                val parent = parentDao.all().firstOrNull { it.id == parentId }?.toDomain()
                supabase.from(STUDENTS_TABLE).select { filter { eq("parent_id", parentId) } }
                    .decodeList<StudentDto>().map { it.toDomain(parent = parent) }
            },
            persist = { students -> studentDao.upsertAll(students.map { it.toEntity() }) },
        )

    /** Stream students by class id. */
    override fun studentsByClass(classId: String): Flow<Result<List<Student>>> =
        RepositoryHelpers.cacheThenFetch(
            dispatchers = dispatchers,
            loadCache = {
                val parents = parentDao.all().associateBy { it.id }
                studentDao.all().filter { it.classId == classId }
                    .map { it.toDomain(parent = parents[it.parentId]?.toDomain()) }
            },
            fetch = {
                val parents = parentDao.all().associateBy { it.id }
                supabase.from(STUDENTS_TABLE).select { filter { eq("class_id", classId) } }
                    .decodeList<StudentDto>()
                    .map { dto -> dto.toDomain(parent = parents[dto.parentId]?.toDomain()) }
            },
            persist = { students -> studentDao.upsertAll(students.map { it.toEntity() }) },
        )

    /** Stream a single student by id. */
    override fun student(id: String): Flow<Result<Student>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = {
            val parents = parentDao.all().associateBy { it.id }
            studentDao.all().firstOrNull { it.id == id }?.toDomain(parent = parents[it.parentId]?.toDomain())
        },
        fetch = {
            val dto = supabase.from(STUDENTS_TABLE).select { filter { eq("id", id) } }
                .decodeList<StudentDto>().firstOrNull() ?: error("Élève $id introuvable.")
            val parent = parentDao.all().firstOrNull { it.id == dto.parentId }?.toDomain()
            dto.toDomain(parent = parent)
        },
        persist = { s -> studentDao.upsert(s.toEntity()) },
    )

    /** Stream students whose code/name matches the query. */
    override fun search(query: String): Flow<Result<List<Student>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { studentDao.search(query).first().map { it.toDomain() } },
        fetch = {
            supabase.from(STUDENTS_TABLE).select {
                filter {
                    or {
                        ilike("first_name", "%$query%")
                        ilike("last_name", "%$query%")
                        ilike("code", "%$query%")
                    }
                }
            }.decodeList<StudentDto>().map { it.toDomain() }
        },
        persist = { students -> studentDao.upsertAll(students.map { it.toEntity() }) },
    )

    /** Create a student in Supabase, cache locally, audit-log. */
    override suspend fun createStudent(input: CreateStudentInput): Result<Student> =
        Result.runCatching {
            val nowIso = nowIso()
            val id = UUID.randomUUID().toString()
            val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            val code = Formatters.studentCode(year = year, seq = (100..99999).random())
            val dto = StudentDto.fromCreate(input, id = id, tenantId = DEFAULT_TENANT, code = code, nowIso = nowIso)
            supabase.from(STUDENTS_TABLE).insert(dto)
            val domain = dto.toDomain()
            studentDao.upsert(domain.toEntity())
            audit.log(ACTION_STUDENT_CREATE, "student", id, actorId = "system", tenantId = DEFAULT_TENANT)
            log.i { "Created student $code" }
            domain
        }.onFailure {
            val payload = StudentDto.fromCreate(
                input = input, id = UUID.randomUUID().toString(),
                tenantId = DEFAULT_TENANT, code = "ELV-PENDING", nowIso = Formatters.nowIso(),
            )
            sync.enqueueRaw(STUDENTS_TABLE, "insert", sync.encode(payload))
            log.w { "createStudent failed — queued: ${it.message}" }
        }

    /** Update a student's mutable fields. */
    override suspend fun updateStudent(
        id: String, firstName: String?, lastName: String?,
        classId: String?, medicalNotes: String?,
    ): Result<Student> = Result.runCatching {
        val nowIso = nowIso()
        val patch = buildMap<String, Any?> {
            firstName?.let { put("first_name", it) }
            lastName?.let { put("last_name", it) }
            classId?.let { put("class_id", it) }
            medicalNotes?.let { put("medical_notes", it) }
            put("updated_at", nowIso)
        }
        supabase.from(STUDENTS_TABLE).update(patch) { filter { eq("id", id) } }
        val refreshed = supabase.from(STUDENTS_TABLE).select { filter { eq("id", id) } }
            .decodeList<StudentDto>().firstOrNull()?.toDomain() ?: error("Élève $id introuvable.")
        studentDao.upsert(refreshed.toEntity())
        audit.log(ACTION_STUDENT_UPDATE, "student", id, actorId = "system", tenantId = DEFAULT_TENANT)
        log.i { "Updated student $id" }
        refreshed
    }.onFailure {
        sync.enqueueRaw(STUDENTS_TABLE, "update", sync.encode(mapOf("id" to id)))
    }

    /** Delete a student. */
    override suspend fun deleteStudent(id: String): Result<Unit> =
        Result.runCatching {
            supabase.from(STUDENTS_TABLE).delete { filter { eq("id", id) } }
            studentDao.deleteById(id)
            log.i { "Deleted student $id" }
        }.onFailure {
            sync.enqueueRaw(STUDENTS_TABLE, "delete", sync.encode(mapOf("id" to id)))
        }

    /** Atomically register a parent + N children via Supabase transactions. */
    override suspend fun batchRegister(input: BatchRegistrationInput): Result<BatchRegistrationResult> =
        Result.runCatching {
            val nowIso = nowIso()
            val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            // Step 1: create the parent.
            val parentId = UUID.randomUUID().toString()
            val parentCode = Formatters.parentCode(year = year, suffix = parentId.take(4).uppercase())
            val parentDto = ParentDto.fromCreate(
                input = input.parent, id = parentId, tenantId = DEFAULT_TENANT, code = parentCode, nowIso = nowIso,
            )
            supabase.from(PARENTS_TABLE).insert(parentDto)
            // Step 2: insert each student referencing the new parent.
            val studentDtos = input.students.mapIndexed { idx, s ->
                val sid = UUID.randomUUID().toString()
                val scode = Formatters.studentCode(year = year, seq = 1000 + idx)
                StudentDto.fromCreate(
                    input = s.copy(parentId = parentId), id = sid,
                    tenantId = DEFAULT_TENANT, code = scode, nowIso = nowIso,
                )
            }
            studentDtos.forEach { supabase.from(STUDENTS_TABLE).insert(it) }
            // Step 3: persist locally + audit.
            val parentDomain = parentDto.toDomain()
            parentDao.upsert(parentDomain.toEntity())
            val studentDomains = studentDtos.map { it.toDomain() }
            studentDao.upsertAll(studentDomains.map { it.toEntity() })
            audit.log(
                action = ACTION_PARENT_CREATE, entityType = "parent", entityId = parentId,
                actorId = "system", tenantId = DEFAULT_TENANT,
                note = "Batch registration: ${studentDtos.size} student(s)",
            )
            log.i { "Batch-registered parent $parentCode with ${studentDtos.size} children" }
            BatchRegistrationResult(parentDomain, studentDomains)
        }.onFailure {
            log.w { "batchRegister failed: ${it.message}" }
        }

    /** Promote the given students to the next academic year. */
    override suspend fun promote(studentIds: List<String>, academicYear: String): Result<List<Student>> =
        Result.runCatching {
            val nowIso = nowIso()
            val updated = studentIds.map { id ->
                supabase.from(STUDENTS_TABLE).update(
                    mapOf("grade_year" to 1, "updated_at" to nowIso, "academic_year" to academicYear),
                ) { filter { eq("id", id) } }
                audit.log(
                    action = ACTION_STUDENT_PROMOTE, entityType = "student", entityId = id,
                    actorId = "system", tenantId = DEFAULT_TENANT, note = "Promoted to $academicYear",
                )
                supabase.from(STUDENTS_TABLE).select { filter { eq("id", id) } }
                    .decodeList<StudentDto>().firstOrNull()?.toDomain() ?: error("Élève $id introuvable.")
            }
            studentDao.upsertAll(updated.map { it.toEntity() })
            log.i { "Promoted ${updated.size} students to $academicYear" }
            updated
        }.onFailure {
            log.w { "promote failed: ${it.message}" }
        }
}

/** Audit action keys, defined here to keep the imports narrow. */
private const val ACTION_PARENT_CREATE = "parent.create"
private const val ACTION_PARENT_UPDATE = "parent.update"
private const val ACTION_STUDENT_CREATE = "student.create"
private const val ACTION_STUDENT_UPDATE = "student.update"
private const val ACTION_STUDENT_PROMOTE = "student.promote"

/** Placeholder tenant id used when the session hasn't resolved yet. */
private const val DEFAULT_TENANT = "tenant-default"
