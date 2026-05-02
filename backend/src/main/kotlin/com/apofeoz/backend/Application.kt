package com.apofeoz.backend

import com.apofeoz.backend.data.*
import com.apofeoz.backend.plugins.*
import com.apofeoz.backend.security.JwtService
import com.apofeoz.backend.service.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    val cfg = AppConfig.load(this)

    DatabaseFactory.init(this)

    val userRepo = UserRepository()
    val refreshRepo = RefreshTokenRepository()
    val workerRepo = WorkerRepository()
    val sessionRepo = SessionRepository()
    val syncRepo = SyncBatchRepository()
    val jwtService = JwtService(cfg)

    val authService = AuthService(cfg, userRepo, refreshRepo, jwtService, syncRepo)
    val userService = UserService(userRepo, workerRepo, refreshRepo)
    val workerService = WorkerService(userRepo, workerRepo, sessionRepo)
    val sessionService = SessionService(sessionRepo)
    val syncService = SyncService(userRepo, workerRepo, syncRepo)
    val reportService = ReportService(cfg, workerRepo, userRepo, sessionRepo)

    configureSerialization()
    configureCors()
    configureStatusPages()
    configureSecurity(jwtService)
    configureRouting(authService, userService, workerService, sessionService, syncService, reportService)

    runBlocking { authService.seedAdminIfNeeded() }

    if (cfg.closeDbOnStop) {
        environment.monitor.subscribe(ApplicationStopped) {
            DatabaseFactory.close()
        }
    }
}
