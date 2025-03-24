package com.vladisc.financial.server.routing.user

import com.vladisc.financial.server.models.PartialUser
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
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate


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
                userRow[UsersTable.email],
                userRow[UsersTable.id],
                userRow[UsersTable.firstName],
                userRow[UsersTable.lastName],
                userRow[UsersTable.dateOfBirth].toString(),
                null
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

            val user = call.receive<PartialUser>()

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


            userRepository.updateUser(userId) {
                if (user.email != null) {
                    it[email] = user.email
                }
                if (user.newPassword != null && user.oldPassword != null) {
                    if (BCrypt.checkpw(user.oldPassword, userRow[password])) {
                        it[password] = BCrypt.hashpw(user.newPassword, BCrypt.gensalt())
                    }
                }
                if (user.firstName != null) {
                    it[firstName] = user.firstName
                }
                if (user.lastName != null) {
                    it[lastName] = user.lastName
                }
                if (user.dateOfBirth != null) {
                    it[dateOfBirth] = LocalDate.parse(user.dateOfBirth)
                }
            }

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