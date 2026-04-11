package com.apofeoz.backend.service

import com.apofeoz.backend.data.WorkerEntity
import org.apache.poi.ss.usermodel.CreationHelper
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * Табель в стиле «табель 2025.xlsx»: колонки A–B зарезервированы, даты в C,
 * на каждого работника две колонки — доля смены (часы / норма) и пустая под будущие выплаты.
 * Без формул и денежных сумм.
 *
 * Строки с датами в колонке C — календарные дни в [reportTimeZone] (в проде обычно Europe/Moscow).
 */
object TimesheetXlsxWriter {

    fun write(
        workers: List<WorkerEntity>,
        fromDate: LocalDate,
        toDate: LocalDate,
        reportTimeZone: String,
        shiftNormHours: Double,
        hoursByWorkerAndDate: Map<Pair<UUID, LocalDate>, Double>,
    ): ByteArray {
        val zone = ZoneId.of(reportTimeZone)
        val lastCol = 2 + workers.size * 2 - 1
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Табель")
            val ch: CreationHelper = wb.creationHelper
            val dateStyle = wb.createCellStyle().apply {
                dataFormat = ch.createDataFormat().getFormat("dd.mm.yyyy")
            }
            val headerStyle = wb.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
            }

            // Закрепить A–B и первые 4 строки (как в образце: шапка + подписи).
            sheet.createFreezePane(2, 4)

            val titleRow = sheet.createRow(0)
            val titleCell = titleRow.createCell(2)
            titleCell.setCellValue(
                "Табель с ${fromDate} по ${toDate}, TZ $reportTimeZone (смены ${shiftNormHours.toInt()}ч; выплаты не заполняются)",
            )
            if (lastCol >= 2) {
                sheet.addMergedRegion(CellRangeAddress(0, 0, 2, lastCol.coerceAtLeast(2)))
            }
            titleCell.cellStyle = headerStyle

            sheet.createRow(1)

            val nameRow = sheet.createRow(2)
            nameRow.createCell(2).setCellValue("фамилия")
            workers.forEachIndexed { i, w ->
                val c0 = 3 + i * 2
                val c1 = c0 + 1
                val cell = nameRow.createCell(c0)
                cell.setCellValue("${w.lastName} ${w.firstName}".trim())
                cell.cellStyle = headerStyle
                sheet.addMergedRegion(CellRangeAddress(2, 2, c0, c1))
            }

            val subRow = sheet.createRow(3)
            subRow.createCell(2).setCellValue("дата")
            workers.forEachIndexed { i, _ ->
                val c0 = 3 + i * 2
                subRow.createCell(c0).setCellValue("смены")
                subRow.createCell(c0 + 1).setCellValue("")
            }

            var excelRow = 4
            var day = fromDate
            while (!day.isAfter(toDate)) {
                val row = sheet.createRow(excelRow++)
                val dateCell = row.createCell(2)
                val instant = day.atStartOfDay(zone).toInstant()
                dateCell.setCellValue(Date.from(instant))
                dateCell.cellStyle = dateStyle

                workers.forEachIndexed { i, w ->
                    val hours = hoursByWorkerAndDate[w.id to day] ?: 0.0
                    val shiftEq = if (shiftNormHours > 0) {
                        (hours / shiftNormHours).roundShiftEquivalentToThreeDecimals()
                    } else {
                        0.0
                    }
                    if (shiftEq > 1e-9) {
                        row.createCell(3 + i * 2).setCellValue(shiftEq)
                    }
                }
                day = day.plusDays(1)
            }

            sheet.setColumnWidth(0, 512)
            sheet.setColumnWidth(1, 512)
            sheet.setColumnWidth(2, 14 * 256)
            for (i in workers.indices) {
                sheet.setColumnWidth(3 + i * 2, 10 * 256)
                sheet.setColumnWidth(4 + i * 2, 6 * 256)
            }

            val bos = ByteArrayOutputStream()
            wb.write(bos)
            return bos.toByteArray()
        }
    }
}
