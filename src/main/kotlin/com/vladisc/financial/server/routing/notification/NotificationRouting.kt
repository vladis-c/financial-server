package com.vladisc.financial.server.routing.notification

import com.vladisc.financial.server.models.EditedBy
import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionType
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.repositories.NotificationRepository
import com.vladisc.financial.server.repositories.TransactionRepository
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.auth.AuthRoutingUtil
import com.vladisc.financial.server.services.OllamaService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
            val notification = call.receive<Notification>()

            // Add notification to notifications table and get notificationId
            val notificationId = notificationRepository.addNotification(notification, userId)

            if (notificationId == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(
                        ErrorRoutingStatus.CONFLICT, "Adding notification error"
                    )
                )
                return@post
            }

            // Get the transaction out of notification
            val ollamaService = OllamaService()
            val partialTransaction = ollamaService.extractTransaction(notification)


            if (partialTransaction != null) {
                if (partialTransaction.amount != null
                    && partialTransaction.name != null
                    && partialTransaction.type != null
                ) {
                    val transaction =
                        Transaction(
                            notification.timestamp,
                            partialTransaction.amount,
                            partialTransaction.name,
                            partialTransaction.type,
                            EditedBy.AUTO,
                            partialTransaction.type != TransactionType.INVOICE
                        )

                    val transactionId = transactionRepository.addTransaction(transaction, userId, notificationId)
                    if (transactionId == null) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorRouting(
                                ErrorRoutingStatus.CONFLICT, "Adding transaction error"
                            )
                        )
                        return@post
                    }
                    call.respond(
                        HttpStatusCode.OK, transaction
                    )
                    return@post

                }
            }
            call.respond(
                HttpStatusCode.PartialContent, notification
            )
        }
    }
}