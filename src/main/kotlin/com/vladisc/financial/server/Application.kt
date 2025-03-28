package com.vladisc.financial.server

import com.vladisc.financial.server.data.DatabaseFactory
import com.vladisc.financial.server.repositories.NotificationRepository
import com.vladisc.financial.server.repositories.TransactionRepository
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.plugins.configureAuthentication
import com.vladisc.financial.server.routing.auth.authRoutes
import com.vladisc.financial.server.routing.user.userRouting
import com.vladisc.financial.server.routing.transaction.transactionRouting
import com.vladisc.financial.server.routing.notification.notificationRouting
import io.github.cdimascio.dotenv.dotenv
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }
    DatabaseFactory.init()
    configureAuthentication()

    val userRepository = UserRepository()
    val transactionRepository = TransactionRepository()
    val notificationRepository = NotificationRepository()

    val dotenv = dotenv()
    val jwtSecret = dotenv["JWT_SECRET"]
    val jwtUrl = dotenv["JWT_URL"]

    routing {
        authRoutes(userRepository, jwtUrl, jwtUrl, jwtSecret)
        userRouting(userRepository, jwtUrl, jwtUrl, jwtSecret)
        transactionRouting(userRepository, transactionRepository, jwtUrl, jwtUrl, jwtSecret)
        notificationRouting(userRepository, notificationRepository, transactionRepository, jwtUrl, jwtUrl, jwtSecret)
    }
}