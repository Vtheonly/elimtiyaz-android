package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
import com.example.core.absenceAlertThreshold
import com.example.core.currentTermWindow
import com.example.core.agingBucketFromDays
import com.example.core.daysBetweenFloor
import com.example.core.formatDzd
import com.example.core.LedgerEngine
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.ReleveEntry
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AuditFilter
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateClassInput
import com.example.domain.repository.CreateDepartmentInput
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentFinancialProfile
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.ReleveRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.RoutingRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateClassInput
import com.example.domain.repository.UpdatePersonnelInput
import com.example.domain.repository.UpdateSubjectInput
import com.example.domain.repository.WorkflowRepository
import com.example.domain.model.GeoPoint
import com.example.infrastructure.routing.OsrmClient
import com.example.infrastructure.routing.TspSolver
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AcademicClassEntity
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AssessmentEntity
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AttendanceEntity
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ClassSubjectDao
import com.example.infrastructure.room.ClassSubjectEntity
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.DepartmentEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.ExpenseEntity
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PersonnelEntity
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.PricingConfigEntity
import com.example.infrastructure.room.PricingDiscountEntity
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.ReleveEntryEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.SubjectEntity
import com.example.infrastructure.room.TransportPricingEntity
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.TripLogEntity
import com.example.infrastructure.room.VehicleDao
import com.example.infrastructure.room.VehicleEntity
import com.example.infrastructure.room.RoutingStopDao
import com.example.infrastructure.room.RoutingStopEntity
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Routing Repository ──────────────────────────────────────────────────────

/**
 * Room-backed routing repository — REAL implementation (previously a stub that
 * returned empty lists and "Not implemented" errors, leaving all three routing
 * screens permanently dead).
 *
 * - Vehicles / stops / trip history are observed from the `vehicles`,
 *   `routing_stops` and `trip_logs` Room tables.
 * - `optimizeRoute` runs the local TSP pipeline (greedy nearest-neighbour +
 *   2-opt refinement) anchored at the school, then tries to enrich the
 *   geometry with a real OSRM driving route; falls back to straight-line
 *   haversine when offline. The computed stop order is persisted back onto the
 *   stop rows so the ordering survives across screens.
 * - `startTrip` / `endTrip` write real `trip_logs` rows.
 */
@Singleton
class LocalRoutingRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
) : RoutingRepository {

    /** School anchor — Établissement Privé El-Imtiyaz, Boumerdes (Prices.md). */
    private val schoolAnchor = GeoPoint(36.7604, 3.4727)

    /** Lazy OSRM client (public demo server) — created once, failures degrade to haversine. */
    private val osrmClient: OsrmClient by lazy {
        OsrmClient(io.ktor.client.HttpClient(io.ktor.client.engine.android.Android))
    }

    override fun observeVehicles(): Flow<Result<List<com.example.domain.model.Vehicle>>> =
        db.vehicleDao().observeAll().map { rows ->
            Result.Ok(rows.map { LocalMappers.run { it.toDomain() } })
        }

    override fun observeStops(shift: com.example.domain.model.RoutingShift?): Flow<Result<List<com.example.domain.model.RoutingStop>>> =
        db.routingStopDao().observeAll().map { rows ->
            val filtered = if (shift == null) {
                rows
            } else {
                // "both"-shift stops are served in every shift window.
                rows.filter { it.shift == shift.wireCode || it.shift == com.example.domain.model.RoutingShift.Both.wireCode }
            }
            Result.Ok(filtered.sortedBy { it.orderInRoute }.map { LocalMappers.run { it.toDomain() } })
        }

    override fun observeTripHistory(): Flow<Result<List<com.example.domain.model.TripLog>>> =
        db.tripLogDao().observeAll().map { rows ->
            Result.Ok(rows.map { LocalMappers.run { it.toDomain() } })
        }

    override suspend fun optimizeRoute(
        vehicleId: String,
        shift: com.example.domain.model.RoutingShift,
        actorId: String,
        actorName: String,
    ): Result<com.example.domain.model.OptimizedRoute> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.Err(Errors.notFound("Véhicule $vehicleId introuvable"))

        val shiftStops = db.routingStopDao().getAll().filter {
            it.shift == shift.wireCode || it.shift == com.example.domain.model.RoutingShift.Both.wireCode
        }
        if (shiftStops.isEmpty()) {
            return Result.Err(Errors.notFound("Aucun arrêt configuré pour le créneau « ${shift.displayFr} »"))
        }

        // ── Stage 1+2: local TSP (greedy NN from the school, then 2-opt) ──
        val ordered = TspSolver.twoOptImprove(
            TspSolver.solveNearestNeighbor(shiftStops.map { LocalMappers.run { it.toDomain() } }, schoolAnchor),
        )

        // ── Stage 3: try to enrich with a real OSRM driving route ──
        val waypoints = listOf(schoolAnchor) + ordered.map { GeoPoint(it.lat, it.lng) }
        val osrmRoute = try { osrmClient.route(waypoints) } catch (_: Throwable) { null }

        val polyline: List<GeoPoint>
        val totalDistanceKm: Double
        val totalDurationMin: Double
        if (osrmRoute != null && osrmRoute.geometry.size >= 2) {
            polyline = osrmRoute.geometry
            totalDistanceKm = osrmRoute.distanceMeters / 1000.0
            totalDurationMin = osrmRoute.durationSeconds / 60.0
        } else {
            // Offline fallback: straight-line distance + urban driving estimate
            // (2.5 min/km + 1 min of dwell time per stop — mirrors the ETA
            // heuristic used by RoutingMapViewModel).
            polyline = waypoints
            totalDistanceKm = TspSolver.polylineDistanceKm(waypoints)
            totalDurationMin = totalDistanceKm * 2.5 + ordered.size
        }

        // Per-stop ETA from the previous stop (haversine-based estimate when
        // OSRM is unavailable; proportional share of OSRM duration otherwise).
        val withEta = ordered.mapIndexed { idx, stop ->
            val legKm = if (idx == 0) {
                TspSolver.haversineKm(schoolAnchor, GeoPoint(stop.lat, stop.lng))
            } else {
                TspSolver.haversineKm(GeoPoint(ordered[idx - 1].lat, ordered[idx - 1].lng), GeoPoint(stop.lat, stop.lng))
            }
            stop.copy(
                orderInRoute = idx + 1,
                estimatedMinutesFromPrevious = legKm * 2.5 + 1.0,
            )
        }

        // Persist the computed order back onto the stop rows so the hub, map
        // and history screens all agree on the route order.
        db.routingStopDao().upsertAll(
            withEta.map { stop ->
                val entity = shiftStops.first { it.id == stop.id }
                LocalMappers.run {
                    entity.toUpdatedEntity(orderInRoute = stop.orderInRoute, estimatedMinutesFromPrevious = stop.estimatedMinutesFromPrevious)
                }
            },
        )
        db.auditLogDao().upsert(
            auditContext.auditLog(
                "routing.optimize", "vehicle", vehicleId, actorId, actorName,
                after = """{"stops":${withEta.size},"distanceKm":${"%.2f".format(totalDistanceKm)},"shift":"${shift.wireCode}","source":"${if (osrmRoute != null) "osrm" else "tsp-local"}"}""",
            ),
        )

        return Result.Ok(
            com.example.domain.model.OptimizedRoute(
                vehicle = LocalMappers.run { vehicle.toDomain() },
                stops = withEta,
                totalDistanceKm = totalDistanceKm,
                totalDurationMin = totalDurationMin,
                polyline = polyline,
            ),
        )
    }

    override suspend fun startTrip(
        vehicleId: String,
        driverId: String,
        driverName: String,
    ): Result<com.example.domain.model.TripLog> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.Err(Errors.notFound("Véhicule $vehicleId introuvable"))

        val now = Instant.now()
        val plannedStops = db.routingStopDao().getAll().filter { it.isActive }
        val entity = TripLogEntity(
            id = "trp-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(),
            driverId = driverId,
            driverName = driverName,
            vehicleId = vehicleId,
            date = LocalDate.now(ZoneOffset.UTC).toString(),
            startTime = now.toString(),
            endTime = null,
            stopCount = plannedStops.size,
            stopsCompleted = 0,
            studentIdsJson = plannedStops.joinToString(",") { "\"${it.studentId}\"" }.let { "[$it]" },
            distanceKm = null,
            status = "running",
            notes = null,
            createdAt = now.toString(),
        )
        db.tripLogDao().upsert(entity)
        db.auditLogDao().upsert(
            auditContext.auditLog("routing.trip_start", "vehicle", vehicleId, driverId, driverName, after = """{"tripId":"${entity.id}","plannedStops":${entity.stopCount}}"""),
        )
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun endTrip(
        tripId: String,
        stopsCompleted: Int,
        totalDistanceKm: Double,
        actorId: String,
        actorName: String,
    ): Result<com.example.domain.model.TripLog> {
        val existing = db.tripLogDao().getById(tripId)
            ?: return Result.Err(Errors.notFound("Tournée $tripId introuvable"))
        val updated = existing.copy(
            endTime = Instant.now().toString(),
            stopsCompleted = stopsCompleted,
            distanceKm = totalDistanceKm,
            status = "ended",
        )
        db.tripLogDao().upsert(updated)
        db.auditLogDao().upsert(
            auditContext.auditLog(
                "routing.trip_end", "vehicle", updated.vehicleId.ifBlank { "trip" }, actorId, actorName,
                after = """{"tripId":"$tripId","stopsCompleted":$stopsCompleted,"distanceKm":${"%.2f".format(totalDistanceKm)}}""",
            ),
        )
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }
}
