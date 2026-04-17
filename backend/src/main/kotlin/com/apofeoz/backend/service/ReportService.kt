package com.apofeoz.backend.service

import com.apofeoz.backend.AppConfig
import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.HoursReportResponse
import com.apofeoz.backend.api.ReportRowResponse
import com.apofeoz.backend.api.ReportTotals
import com.apofeoz.backend.api.TimesheetDayCellResponse
import com.apofeoz.backend.api.TimesheetDayRowResponse
import com.apofeoz.backend.api.TimesheetReportResponse
import com.apofeoz.backend.api.TimesheetWorkerResponse
import com.apofeoz.backend.data.SessionEntity
import com.apofeoz.backend.data.SessionRepository
import com.apofeoz.backend.data.UserRepository
import com.apofeoz.backend.data.WorkerRepository
import com.apofeoz.backend.domain.Role
import io.ktor.http.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Отчёты и табель: календарные дни и разбивка смены по полуночи считаются в [AppConfig.reportTimeZone]
 * (по умолчанию **Europe/Moscow**). Параметры `from` / `to` и «вчера» — даты в этой зоне.
 */
class ReportService(
    private val cfg: AppConfig,
    private val workers: WorkerRepository,
    private val users: UserRepository,
    private val sessions: SessionRepository,
) {

    suspend fun hoursByWorkerPreviousDay(role: Role, dateOverride: String?): HoursReportResponse {
        if (role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Admin only")
        }
        val zone = try {
            ZoneId.of(cfg.reportTimeZone)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.InternalServerError, "config_error", "Invalid report timezone in config")
        }
        val reportDate = if (dateOverride != null) {
            try {
                LocalDate.parse(dateOverride)
            } catch (_: Exception) {
                throw ApiException(HttpStatusCode.BadRequest, "validation_error", "date must be YYYY-MM-DD")
            }
        } else {
            LocalDate.now(zone).minusDays(1)
        }
        val dayStart = reportDate.atStartOfDay(zone).toOffsetDateTime()
        val dayEnd = reportDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime()

        val activeWorkers = workers.listActive()
        val shiftNorm = 8.0
        val rows = activeWorkers.map { w ->
            val hours = sessions.sumClosedHoursForWorkerOnDay(w.id, dayStart, dayEnd)
            val shiftEq = (hours / shiftNorm).roundShiftEquivalentToThreeDecimals()
            val foremanUser = users.findById(w.foremanId)
            val foremanName = foremanUser?.let { "${it.firstName} ${it.lastName}" }
            ReportRowResponse(
                workerId = w.id.toString(),
                firstName = w.firstName,
                lastName = w.lastName,
                foremanId = w.foremanId.toString(),
                foremanDisplayName = foremanName,
                hours = hours,
                shiftEquivalent = shiftEq,
            )
        }
        val totalHours = rows.sumOf { it.hours }
        val totalShifts = rows.sumOf { it.shiftEquivalent }.roundShiftEquivalentToThreeDecimals()
        return HoursReportResponse(
            reportDate = reportDate.toString(),
            fromDate = reportDate.toString(),
            toDate = reportDate.toString(),
            timezone = cfg.reportTimeZone,
            shiftNormHours = 8,
            rows = rows,
            totals = ReportTotals(hours = totalHours, shiftEquivalent = totalShifts),
        )
    }

    suspend fun hoursByWorkerRange(role: Role, from: String?, to: String?): HoursReportResponse {
        if (role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Admin only")
        }
        val range = parseRange(from, to)

        val activeWorkers = workers.listActive()
        val shiftNorm = 8.0
        val rows = activeWorkers.map { w ->
            val hours = sessions.sumClosedHoursForWorkerInRange(w.id, range.start, range.endExclusive)
            val shiftEq = (hours / shiftNorm).roundShiftEquivalentToThreeDecimals()
            val foremanUser = users.findById(w.foremanId)
            val foremanName = foremanUser?.let { "${it.firstName} ${it.lastName}" }
            ReportRowResponse(
                workerId = w.id.toString(),
                firstName = w.firstName,
                lastName = w.lastName,
                foremanId = w.foremanId.toString(),
                foremanDisplayName = foremanName,
                hours = hours,
                shiftEquivalent = shiftEq,
            )
        }
        val totalHours = rows.sumOf { it.hours }
        val totalShifts = rows.sumOf { it.shiftEquivalent }.roundShiftEquivalentToThreeDecimals()
        return HoursReportResponse(
            reportDate = "${range.fromDate}..${range.toDate}",
            fromDate = range.fromDate.toString(),
            toDate = range.toDate.toString(),
            timezone = cfg.reportTimeZone,
            shiftNormHours = 8,
            rows = rows,
            totals = ReportTotals(hours = totalHours, shiftEquivalent = totalShifts),
        )
    }

    suspend fun timesheet(role: Role, from: String?, to: String?): TimesheetReportResponse {
        if (role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Admin only")
        }
        val range = parseRange(from, to)
        val activeWorkers = workers.listActive()
        val closed = sessions.listClosedSessionsOverlapping(range.start, range.endExclusive)
        val hoursByDay = aggregateHoursByWorkerAndDate(closed, range.zone, range.start, range.endExclusive)
        val shiftNorm = 8.0
        val workerColumns = activeWorkers.map { worker ->
            val foremanUser = users.findById(worker.foremanId)
            TimesheetWorkerResponse(
                workerId = worker.id.toString(),
                firstName = worker.firstName,
                lastName = worker.lastName,
                foremanId = worker.foremanId.toString(),
                foremanDisplayName = foremanUser?.let { "${it.firstName} ${it.lastName}" },
            )
        }
        val rows = generateSequence(range.fromDate) { current ->
            current.plusDays(1).takeIf { !it.isAfter(range.toDate) }
        }.map { date ->
            TimesheetDayRowResponse(
                date = date.toString(),
                cells = workerColumns.map { worker ->
                    val hours = hoursByDay[UUID.fromString(worker.workerId) to date] ?: 0.0
                    TimesheetDayCellResponse(
                        workerId = worker.workerId,
                        hours = hours,
                        shiftEquivalent = (hours / shiftNorm).roundShiftEquivalentToThreeDecimals(),
                    )
                },
            )
        }.toList()
        return TimesheetReportResponse(
            title = "Табель учёта рабочего времени",
            fromDate = range.fromDate.toString(),
            toDate = range.toDate.toString(),
            timezone = cfg.reportTimeZone,
            shiftNormHours = shiftNorm.toInt(),
            workers = workerColumns,
            rows = rows,
        )
    }

    suspend fun timesheetXlsx(role: Role, from: String?, to: String?): ByteArray {
        if (role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Admin only")
        }
        val range = parseRange(from, to)

        val activeWorkers = workers.listActive()
        val closed = sessions.listClosedSessionsOverlapping(range.start, range.endExclusive)
        val hoursByDay = aggregateHoursByWorkerAndDate(closed, range.zone, range.start, range.endExclusive)
        val shiftNorm = 8.0
        return TimesheetXlsxWriter.write(
            workers = activeWorkers,
            fromDate = range.fromDate,
            toDate = range.toDate,
            reportTimeZone = cfg.reportTimeZone,
            shiftNormHours = shiftNorm,
            hoursByWorkerAndDate = hoursByDay,
        )
    }

    private fun parseRange(from: String?, to: String?): ReportRange {
        val zone = try {
            ZoneId.of(cfg.reportTimeZone)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.InternalServerError, "config_error", "Invalid report timezone in config")
        }
        if (from.isNullOrBlank() || to.isNullOrBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "from and to are required (YYYY-MM-DD)")
        }
        val fromDate = try {
            LocalDate.parse(from)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "from must be YYYY-MM-DD")
        }
        val toDate = try {
            LocalDate.parse(to)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "to must be YYYY-MM-DD")
        }
        if (toDate.isBefore(fromDate)) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "to must be >= from")
        }
        return ReportRange(
            zone = zone,
            fromDate = fromDate,
            toDate = toDate,
            start = fromDate.atStartOfDay(zone).toOffsetDateTime(),
            endExclusive = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
        )
    }

    private fun aggregateHoursByWorkerAndDate(
        sessions: List<SessionEntity>,
        zone: ZoneId,
        rangeStart: OffsetDateTime,
        rangeEndExclusive: OffsetDateTime,
    ): Map<Pair<UUID, LocalDate>, Double> {
        val seconds = mutableMapOf<Pair<UUID, LocalDate>, Double>()
        for (s in sessions) {
            val end = s.endAt ?: continue
            val clippedStart = maxOf(s.startAt, rangeStart)
            val clippedEnd = minOf(end, rangeEndExclusive)
            if (!clippedEnd.isAfter(clippedStart)) continue
            distributeSessionToDays(s.workerId, clippedStart, clippedEnd, zone, seconds)
        }
        return seconds.mapValues { (_, sec) ->
            val rounded = kotlin.math.round(sec / 60.0) * 60.0
            rounded / 3600.0
        }
    }

    private fun distributeSessionToDays(
        workerId: UUID,
        start: OffsetDateTime,
        end: OffsetDateTime,
        zone: ZoneId,
        secondsAcc: MutableMap<Pair<UUID, LocalDate>, Double>,
    ) {
        var cursor = start
        while (cursor.isBefore(end)) {
            val date = cursor.atZoneSameInstant(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
            val segmentEnd = minOf(end, nextMidnight)
            val sec = java.time.Duration.between(cursor, segmentEnd).seconds.toDouble()
            if (sec > 0) {
                val key = workerId to date
                secondsAcc[key] = (secondsAcc[key] ?: 0.0) + sec
            }
            cursor = segmentEnd
        }
    }

    private data class ReportRange(
        val zone: ZoneId,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val start: OffsetDateTime,
        val endExclusive: OffsetDateTime,
    )
}
