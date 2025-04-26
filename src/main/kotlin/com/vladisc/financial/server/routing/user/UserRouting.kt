package com.vladisc.financial.server.routing.user

import com.vladisc.financial.server.models.User
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.models.UsersTable
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.auth.AuthRoutingUtil
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive


fun Route.userRouting(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/users") {
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

            // Return user data
            val user = User(
                email = userRow[UsersTable.email],
                firstName = userRow[UsersTable.firstName],
                lastName = userRow[UsersTable.lastName],
                dateOfBirth = userRow[UsersTable.dateOfBirth].toString(),
                company = userRow[UsersTable.company],
                uid = userRow[UsersTable.id].toString()
            )
            call.respond(HttpStatusCode.OK, user)
        }

        put("/") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
                )
                return@put
            }
            // Get user id from the token
            val userId = UserRoutingUtil.decodeTokenToUid(
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

            // Get user via body
            val body = call.receiveNullable<Map<String, JsonElement>>()
            if (body == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                )
                return@put
            }

            val user = User(
                firstName = body["firstName"]?.jsonPrimitive?.contentOrNull,
                lastName = body["lastName"]?.jsonPrimitive?.contentOrNull,
                email = body["email"]?.jsonPrimitive?.contentOrNull,
                password = body["password"]?.jsonPrimitive?.contentOrNull,
                newPassword = body["newPassword"]?.jsonPrimitive?.contentOrNull,
                dateOfBirth = body["dateOfBirth"]?.jsonPrimitive?.contentOrNull,
                company = body["company"]?.jsonPrimitive?.contentOrNull,
                uid = userId.toString()
            )

            userRepository.updateUser(userId, user, userRow[UsersTable.password])

            // Return user data
            call.respond(HttpStatusCode.OK, user)
        }

        delete("/") {
            // Check if access token is provided
            val accessTokenCookie = call.request.cookies["accessToken"]
            if (accessTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access token provided")
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

            // Deleter user from DB
            val userRow = userRepository.deleteUser(userId)
            if (userRow) {
                call.respond(HttpStatusCode.OK)
                return@delete
            }

            call.respond(
                HttpStatusCode.Conflict,
                ErrorRouting(ErrorRoutingStatus.CONFLICT, "Error when deleting the user")
            )
        }
    }
}