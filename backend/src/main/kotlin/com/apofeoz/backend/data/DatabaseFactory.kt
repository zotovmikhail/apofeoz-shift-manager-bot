package com.apofeoz.backend.data

import com.apofeoz.backend.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)
    lateinit var dataSource: HikariDataSource
    private var currentJdbcUrl: String? = null

    fun init(app: Application) {
        val cfg = AppConfig.load(app)
        if (::dataSource.isInitialized && !dataSource.isClosed && currentJdbcUrl == cfg.jdbcUrl) {
            return
        }
        if (::dataSource.isInitialized) {
            log.warn("Re-initializing database pool (closing previous)")
            close()
        }
        val hc = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.dbUser
            password = cfg.dbPassword
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        dataSource = HikariDataSource(hc)
        currentJdbcUrl = cfg.jdbcUrl
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(dataSource)
        log.info("Database migrated and connected: {}", cfg.jdbcUrl)
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
        currentJdbcUrl = null
    }
}
