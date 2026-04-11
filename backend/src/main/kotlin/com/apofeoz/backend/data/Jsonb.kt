package com.apofeoz.backend.data

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject
import java.sql.ResultSet

/**
 * Minimal jsonb support for Exposed without extra modules.
 * Stores value as String in Kotlin, writes PGobject(type=jsonb) to PostgreSQL.
 */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "jsonb"

    override fun setParameter(stmt: org.jetbrains.exposed.sql.statements.api.PreparedStatementApi, index: Int, value: Any?) {
        if (value == null) {
            stmt.setNull(index, this)
            return
        }
        val obj = PGobject().apply {
            type = "jsonb"
            this.value = value as String
        }
        stmt[index] = obj
    }

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value ?: ""
        else -> value.toString()
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = rs.getObject(index)
}

fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())

