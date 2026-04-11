package com.apofeoz.backend.service

import com.apofeoz.backend.AppConfig
import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.HoursReportResponse
import com.apofeoz.backend.api.ReportRowResponse
import com.apofeoz.backend.api.ReportTotals
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
        val start = fromDate.atStartOfDay(zone).toOffsetDateTime()
        val endExclusive = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime()

        val activeWorkers = workers.listActive()
        val shiftNorm = 8.0
        val rows = activeWorkers.map { w ->
            val hours = sessions.sumClosedHoursForWorkerInRange(w.id, start, endExclusive)
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
            reportDate = "${fromDate}..${toDate}",
            fromDate = fromDate.toString(),
            toDate = toDate.toString(),
            timezone = cfg.reportTimeZone,
            shiftNormHours = 8,
            rows = rows,
            totals = ReportTotals(hours = totalHours, shiftEquivalent = totalShifts),
        )
    }

    suspend fun timesheetXlsx(role: Role, from: String?, to: String?): ByteArray {
        if (role != Role.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Admin only")
        }
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
        val start = fromDate.atStartOfDay(zone).toOffsetDateTime()
        val endExclusive = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime()

        val activeWorkers = workers.listActive()
        val closed = sessions.listClosedSessionsOverlapping(start, endExclusive)
        val hoursByDay = aggregateHoursByWorkerAndDate(closed, zone, start, endExclusive)
        val shiftNorm = 8.0
        return TimesheetXlsxWriter.write(
            workers = activeWorkers,
            fromDate = fromDate,
            toDate = toDate,
            reportTimeZone = cfg.reportTimeZone,
            shiftNormHours = shiftNorm,
            hoursByWorkerAndDate = hoursByDay,
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
}
