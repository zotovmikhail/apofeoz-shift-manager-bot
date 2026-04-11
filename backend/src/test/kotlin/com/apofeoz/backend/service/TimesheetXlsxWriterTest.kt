package com.apofeoz.backend.service

import com.apofeoz.backend.data.WorkerEntity
import com.apofeoz.backend.domain.WorkerStatus
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimesheetXlsxWriterTest {

    /** Совпадает с дефолтом `app.reportTimeZone` в проде — отчёты по московским календарным суткам. */
    private val zoneId = "Europe/Moscow"
    private val dateFormatter = DataFormatter(Locale.forLanguageTag("ru-RU"))

    private fun worker(
        id: UUID,
        firstName: String = "Иван",
        lastName: String = "Тестов",
    ): WorkerEntity = WorkerEntity(
        id = id,
        userId = null,
        foremanId = UUID.randomUUID(),
        firstName = firstName,
        lastName = lastName,
        position = null,
        status = WorkerStatus.ACTIVE,
        createdAt = OffsetDateTime.parse("2025-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2025-01-01T00:00:00Z"),
    )

    /** Колонка «смены» для работника с индексом `workerIndex` (0 — первая пара D/E). */
    private fun shiftColumnIndex(workerIndex: Int): Int = 3 + workerIndex * 2

    private fun readShiftNumeric(sheet: org.apache.poi.ss.usermodel.Sheet, excelRow: Int, workerIndex: Int): Double? {
        val row = sheet.getRow(excelRow) ?: return null
        val cell = row.getCell(shiftColumnIndex(workerIndex)) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.BLANK, CellType._NONE -> null
            else -> null
        }
    }

    private val displayDateParser = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.forLanguageTag("ru-RU"))

    private fun readDateLocal(sheet: org.apache.poi.ss.usermodel.Sheet, excelRow: Int): LocalDate {
        val cell = assertNotNull(sheet.getRow(excelRow)?.getCell(2))
        assertTrue(DateUtil.isCellDateFormatted(cell), "column C must be a date cell")
        val s = dateFormatter.formatCellValue(cell).trim()
        return LocalDate.parse(s, displayDateParser)
    }

    private fun readDateDisplayString(sheet: org.apache.poi.ss.usermodel.Sheet, excelRow: Int): String {
        val cell = assertNotNull(sheet.getRow(excelRow)?.getCell(2))
        return dateFormatter.formatCellValue(cell)
    }

    @Test
    fun `eight hours maps to one full shift in excel`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 10)
        val to = from
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = to,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = mapOf((wid to from) to 8.0),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals(from, readDateLocal(sheet, 4))
            assertEquals(1.0, readShiftNumeric(sheet, 4, 0)!!, 1e-9)
        }
    }

    @Test
    fun `zero hours leaves shift cell empty`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 11)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = emptyMap(),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertNull(readShiftNumeric(sheet, 4, 0))
        }
    }

    @Test
    fun `four hours maps to half shift rounded to three decimals`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 12)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = mapOf((wid to from) to 4.0),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            assertEquals(0.5, readShiftNumeric(wb.getSheetAt(0), 4, 0)!!, 1e-9)
        }
    }

    @Test
    fun `two workers get correct columns for same day`() {
        val w0 = UUID.randomUUID()
        val w1 = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 13)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(w0, "А"), worker(w1, "Б")),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = mapOf(
                (w0 to from) to 8.0,
                (w1 to from) to 4.0,
            ),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals(1.0, readShiftNumeric(sheet, 4, 0)!!, 1e-9)
            assertEquals(0.5, readShiftNumeric(sheet, 4, 1)!!, 1e-9)
        }
    }

    @Test
    fun `shift equivalent uses three decimal rounding like json report`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 14)
        val hours = 7.88
        val expected = (hours / 8.0).roundShiftEquivalentToThreeDecimals()
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = mapOf((wid to from) to hours),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            assertEquals(expected, readShiftNumeric(wb.getSheetAt(0), 4, 0)!!, 1e-9)
            assertEquals(0.985, expected, 1e-9)
        }
    }

    @Test
    fun `date column lists each calendar day in range`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 1)
        val to = LocalDate.of(2025, 6, 3)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = to,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = mapOf((wid to from.plusDays(1)) to 8.0),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals(from, readDateLocal(sheet, 4))
            assertEquals(from.plusDays(1), readDateLocal(sheet, 5))
            assertEquals(to, readDateLocal(sheet, 6))
            assertNull(readShiftNumeric(sheet, 4, 0))
            assertEquals(1.0, readShiftNumeric(sheet, 5, 0)!!, 1e-9)
            assertNull(readShiftNumeric(sheet, 6, 0))
        }
    }

    @Test
    fun `header shows worker last and first name merged pair`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 6, 15)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid, firstName = "Пётр", lastName = "Сидоров")),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = emptyMap(),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val nameCell = wb.getSheetAt(0).getRow(2).getCell(3)
            assertEquals("Сидоров Пётр", nameCell.stringCellValue.trim())
        }
    }

    @Test
    fun `date cell displays dd dot mm dot yyyy`() {
        val wid = UUID.randomUUID()
        val from = LocalDate.of(2025, 12, 3)
        val bytes = TimesheetXlsxWriter.write(
            workers = listOf(worker(wid)),
            fromDate = from,
            toDate = from,
            reportTimeZone = zoneId,
            shiftNormHours = 8.0,
            hoursByWorkerAndDate = emptyMap(),
        )
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val s = readDateDisplayString(wb.getSheetAt(0), 4)
            assertTrue(s.contains("03") && s.contains("12") && s.contains("2025"), "got: $s")
        }
    }
}
