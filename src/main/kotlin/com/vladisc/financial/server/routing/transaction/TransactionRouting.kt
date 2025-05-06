package com.vladisc.financial.server.routing.transaction

import com.vladisc.financial.server.models.EditedBy
import com.vladisc.financial.server.models.InvoiceStatus
import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionType
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.repositories.TransactionRepository
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.auth.AuthRoutingUtil
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

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
            val transactionId = call.parameters["id"]?.toInt()
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

            // Get query parameters, if any
            val queryParams = TransactionRoutingUtil.getTransactionQueries(call.request.queryParameters)

            // Check for all transactions for this user + query
            val transactionRows = transactionRepository.getTransactions(userId, queryParams)
            if (transactionRows.isEmpty()) {
                call.respond(
                    HttpStatusCode.OK,
                    emptyArray<Transaction>()
                )
                return@get
            }

            val transactions = TransactionRoutingUtil.parseTransactions(transactionRows)
            call.respond(
                HttpStatusCode.OK, transactions
            )

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
            val body = call.receiveNullable<Map<String, JsonElement>>()

            if (body == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                )
                return@post
            }

            val transaction = Transaction(
                timestamp = body["timestamp"]?.jsonPrimitive?.contentOrNull,
                amount = body["amount"]?.jsonPrimitive?.floatOrNull,
                name = body["name"]?.jsonPrimitive?.contentOrNull,
                type = body["type"]?.jsonPrimitive?.contentOrNull?.let { TransactionType.valueOf(it) },
                editedBy = body["editedBy"]?.jsonPrimitive?.contentOrNull?.let { EditedBy.valueOf(it) },
                dueDate = body["dueDate"]?.jsonPrimitive?.contentOrNull,
                payDate = body["payDate"]?.jsonPrimitive?.contentOrNull,
                invoiceStatus = body["invoiceStatus"]?.jsonPrimitive?.contentOrNull?.let { InvoiceStatus.valueOf(it) }
            )

            // Add transaction to transactions table
            val transactionId = transactionRepository.addTransaction(transaction, userId)

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

        }

        put("/{id}") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@put
            }

            // Check if transaction id has been provided
            val transactionId = call.parameters["id"]?.toInt()
            if (transactionId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "No transaction id provided")
                )
                return@put
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
                return@put
            }

            // Check if user exists in DB
            val userRow = userRepository.findUserById(userId)
            if (userRow == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User not found")
                )
                return@put
            }

            // Check transaction parameters via body
            val body = call.receiveNullable<Map<String, JsonElement>>()

            if (body == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                )
                return@put
            }

            val transaction = Transaction(
                timestamp = body["timestamp"]?.jsonPrimitive?.contentOrNull,
                amount = body["amount"]?.jsonPrimitive?.floatOrNull,
                name = body["name"]?.jsonPrimitive?.contentOrNull,
                type = body["type"]?.jsonPrimitive?.contentOrNull?.let { TransactionType.valueOf(it) },
                editedBy = body["editedBy"]?.jsonPrimitive?.contentOrNull?.let { EditedBy.valueOf(it) },
                dueDate = body["dueDate"]?.jsonPrimitive?.contentOrNull,
                invoiceStatus = body["invoiceStatus"]?.jsonPrimitive?.contentOrNull?.let { InvoiceStatus.valueOf(it) }
            )

            transactionRepository.updateTransaction(transaction, transactionId)

            // Return transaction data
            call.respond(HttpStatusCode.OK, transaction)

        }

        delete("/{id}") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@delete
            }

            // Check if transaction id has been provided
            val transactionId = call.parameters["id"]?.toInt()
            if (transactionId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "No transaction id provided")
                )
                return@delete
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
                return@delete
            }


            val transactionRow = transactionRepository.deleteTransaction(transactionId)
            if (transactionRow) {
                call.respond(HttpStatusCode.OK)
                return@delete
            }

            call.respond(
                HttpStatusCode.Conflict,
                ErrorRouting(ErrorRoutingStatus.CONFLICT, "Error when deleting the transaction")
            )

        }

        patch("/{id}/invoice") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@patch
            }

            // Check if transaction id has been provided
            val transactionId = call.parameters["id"]?.toInt()
            if (transactionId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "No transaction id provided")
                )
                return@patch
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
                return@patch
            }

            // Get invoice status from the body
            val body = call.receiveNullable<Map<String, JsonElement>>()

            if (body == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                )
                return@patch
            }

            val invoiceStatus = body["invoiceStatus"]?.jsonPrimitive?.contentOrNull?.let {
                try {
                    InvoiceStatus.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            if (invoiceStatus == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body parameter invoiceStatus is missing")
                )
                return@patch
            }

            // change invoice status:
            val transactionRow = transactionRepository.changeInvoiceStatus(transactionId, invoiceStatus)

            if (transactionRow) {
                call.respond(HttpStatusCode.OK)
                return@patch
            }
            call.respond(
                HttpStatusCode.Conflict,
                ErrorRouting(ErrorRoutingStatus.CONFLICT, "Error when changing invoice status")
            )
        }
    }
}