package com.vladisc.financial.server.routing.notification

import com.vladisc.financial.server.models.*
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.repositories.NotificationRepository
import com.vladisc.financial.server.repositories.TransactionRepository
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.auth.AuthRoutingUtil
import com.vladisc.financial.server.routing.transaction.TransactionRoutingUtil
import com.vladisc.financial.server.services.OllamaService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Route.notificationRouting(
    userRepository: UserRepository,
    notificationRepository: NotificationRepository,
    transactionRepository: TransactionRepository,
    jwtIssuer: String,
    jwtAudience: String,
    jwtSecret: String
) {
    route("users/notifications") {
        get("/") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@get
            }

            // Get user id from the token
            val userId = AuthRoutingUtil.decodeTokenToUid(
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                accessTokenCookie
            )

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "Token expired")
                )
                return@get
            }

            // Check if user exists in DB
            val userRow = userRepository.findUserById(userId)
            if (userRow == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User not found")
                )
                return@get
            }

            // Get query parameters, if any
            val queryParams = NotificationRoutingUtil.getNotificationQueries(call.request.queryParameters)

            // Check for all notifications for this user + query
            val notificationRows = notificationRepository.getNotifications(userId, queryParams)
            if (notificationRows.isEmpty()) {
                call.respond(
                    HttpStatusCode.OK,
                    emptyArray<Notification>()
                )
                return@get
            }

            val notifications = NotificationRoutingUtil.parseNotifications(notificationRows)
            call.respond(HttpStatusCode.OK, notifications)

        }
        post("/") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@post
            }

            // Get user id from the token
            val userId = AuthRoutingUtil.decodeTokenToUid(
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                accessTokenCookie
            )

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "Token expired")
                )
                return@post
            }

            // Check if user exists in DB
            val userRow = userRepository.findUserById(userId)
            if (userRow == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User not found")
                )
                return@post
            }

            // Get notification via body
            val body = call.receiveNullable<JsonArray>()
            if (body == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                )
                return@post
            }

            val notifications = body.map { jsonElement ->
                val obj = jsonElement.jsonObject
                Notification(
                    timestamp = obj["timestamp"]?.jsonPrimitive?.contentOrNull,
                    title = obj["title"]?.jsonPrimitive?.contentOrNull,
                    body = obj["body"]?.jsonPrimitive?.contentOrNull,
                    id = null,
                )
            }

            // Get all types of latest transactions (1 each)
            val prevTransactionsRows = transactionRepository.getLatestTransactionsByType(userId)
            val prevTransactions = TransactionRoutingUtil.parseTransactions(prevTransactionsRows)
            val prevTransactionsIds = prevTransactionsRows.map { it[TransactionsTable.id] }

            // Get corresponding notifications of latest transactions
            val prevNotificationRows = notificationRepository.getLastNotifications(userId, prevTransactionsIds)
            val prevNotifications = NotificationRoutingUtil.parseNotifications(prevNotificationRows)

            // Get the transaction out of notification
            val ollamaService = OllamaService()
            val transactionsFromLLM = ollamaService.extractTransactions(
                notifications,
                userRow[UsersTable.firstName],
                userRow[UsersTable.lastName],
                userRow[UsersTable.company],
                prevTransactions,
                prevNotifications
            )

            val transactionsIds: List<String>?
            val transactions: List<Transaction>?

            if (transactionsFromLLM == null) {
                transactions = notifications.map { notification ->
                    Transaction(
                        TransactionRoutingUtil.generateTransactionId(notification.timestamp),
                        notification.timestamp,
                        0.toFloat(),
                        "undefined",
                        null,
                        EditedBy.AUTO,
                        null,
                        null
                    )
                }
                // partially add Transactions
                transactionsIds = transactionRepository.addTransactions(transactions, userId)
            } else {
               transactions = transactionsFromLLM.mapIndexed { index, transaction ->
                    Transaction(
                        null,
                        notifications[index].timestamp,
                        transaction.amount,
                        transaction.name,
                        transaction.type,
                        EditedBy.AUTO,
                        transaction.dueDate,
                        transaction.payDate,
                        transaction.invoiceStatus
                    )
                }
                // add transactions
                transactionsIds = transactionRepository.addTransactions(transactions, userId)

            }

            if (transactionsIds.isEmpty()) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(
                        ErrorRoutingStatus.CONFLICT, "Adding transactions error"
                    )
                )
                return@post
            } else {
                // add notifications
                notificationRepository.addNotifications(notifications, userId, transactionsIds)
            }
            call.respond(
                HttpStatusCode.OK,
                transactions
            )

        }
    }
}