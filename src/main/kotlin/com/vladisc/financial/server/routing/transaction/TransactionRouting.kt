package com.vladisc.financial.server.routing.transaction


import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.repositories.TransactionRepository
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.auth.AuthRoutingUtil
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transactionRouting(
    userRepository: UserRepository,
    transactionRepository: TransactionRepository,
    jwtIssuer: String,
    jwtAudience: String,
    jwtSecret: String
) {
    route("/users/transactions") {
        get("/{id}") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@get
            }

            // Check if transaction id has been provided
            val transactionId = call.parameters["id"]
            if (transactionId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "No transaction id provided")
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

            // Find transaction from DB
            val transactionRow = transactionRepository.findTransaction(transactionId)
            if (transactionRow == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "Transaction not found")
                )
                return@get
            }

            val transaction = TransactionRoutingUtil.parseTransaction(transactionRow)
            call.respond(
                HttpStatusCode.OK, transaction
            )

        }

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

            val transactionRows = transactionRepository.getTransactions(userId)
            if (transactionRows.isEmpty()) {
                call.respond(
                    HttpStatusCode.OK,
                    emptyArray<Transaction>()
                )
                return@get
            }

            val transactions = TransactionRoutingUtil.parseTransactions(transactionRows)
            call.respond(
                HttpStatusCode.OK,
                transactions
            )
            return@get

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

            // Get transaction via body
            val transaction = call.receive<Transaction>()

            // Generate transaction id based on date, name, amount
            val transactionId =
                TransactionRoutingUtil.generateTransactionId(
                    transaction.timestamp,
                    transaction.name,
                    transaction.amount.toString()
                )

            // Add transaction to transactions table
            val success = transactionRepository.addTransaction(transaction, transactionId, userId)

            if (!success) {
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

        }

        put("/{id}") {}

        delete("/{id}") {}
    }
}