package com.vladisc.financial.server.routing.auth

import com.vladisc.financial.server.models.*
import com.vladisc.financial.server.plugins.ErrorRouting
import com.vladisc.financial.server.plugins.ErrorRoutingStatus
import com.vladisc.financial.server.repositories.UserRepository
import com.vladisc.financial.server.routing.RoutingUtil
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

fun Route.authRoutes(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/auth") {
        post("/signup") {
            try {
                // Get user via body
                val body = call.receiveNullable<Map<String, JsonElement>>()
                if (body == null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(ErrorRoutingStatus.PARAMETER_MISSING, "Body is empty")
                    )
                    return@post
                }

                val user = User(
                    firstName = body["firstName"]?.jsonPrimitive?.contentOrNull,
                    lastName = body["lastName"]?.jsonPrimitive?.contentOrNull,
                    email = body["email"]?.jsonPrimitive?.contentOrNull,
                    password = body["password"]?.jsonPrimitive?.contentOrNull,
                    dateOfBirth = body["dateOfBirth"]?.jsonPrimitive?.contentOrNull,
                    company = body["company"]?.jsonPrimitive?.contentOrNull,
                )

                if (user.password.isNullOrBlank() || user.email.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRouting(
                            ErrorRoutingStatus.PARAMETER_MISSING, "Password or email is missing"
                        )
                    )
                    return@post
                }

                if (!RoutingUtil.isValidEmail(user.email)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.INVALID_FORMAT, "Invalid format of email"
                        )
                    )
                    return@post
                }

                if (!RoutingUtil.isValidPassword(user.password)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.INVALID_FORMAT, "Password should be at least 4 characters long"
                        )
                    )
                    return@post
                }

                // Add new user
                val success = userRepository.addUser(user)

                if (!success) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.CONFLICT, "Email already registered"
                        )
                    )
                    return@post
                }

                // Get created user
                val userRow = userRepository.findUser(user.email)
                if (userRow === null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User ${user.email} not found")
                    )
                    return@post
                }

                // Generate tokens for new user user
                val tokenResponse = AuthRoutingUtil.generateTokens(
                    userRow[UsersTable.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(AuthRoutingUtil.accessTokenExpiryDate),
                    Date(AuthRoutingUtil.refreshTokenExpiryDate),
                )
                AuthRoutingUtil.setTokenHeader(call, tokenResponse)
                call.respond(HttpStatusCode.Created, tokenResponse)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorRouting(
                        ErrorRoutingStatus.GENERIC_ERROR, e.message
                    )
                )
            }
        }

        post("/login") {
            try {
                val user = call.receive<UserCredentials>()

                if (user.password.isBlank() || user.email.isBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRouting(
                            ErrorRoutingStatus.PARAMETER_MISSING, "Password or email is missing"
                        )
                    )
                    return@post
                }

                if (!RoutingUtil.isValidEmail(user.email)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.INVALID_FORMAT, "Invalid format of email"
                        )
                    )
                    return@post
                }

                if (!RoutingUtil.isValidPassword(user.password)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.INVALID_FORMAT, "Password should be at least 4 characters long"
                        )
                    )
                    return@post
                }

                // check for user by email in DB
                val userRow = userRepository.findUser(user.email)

                if (userRow == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User ${user.email} not found")
                    )
                    return@post
                }

                // Compare input pw and stored pw in DB
                if (!BCrypt.checkpw(user.password, userRow[UsersTable.password])) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "Invalid credentials")
                    )
                    return@post
                }

                // Generate new access and refresh tokens
                val tokenResponse = AuthRoutingUtil.generateTokens(
                    userRow[UsersTable.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(AuthRoutingUtil.accessTokenExpiryDate),
                    Date(AuthRoutingUtil.refreshTokenExpiryDate),
                )
                AuthRoutingUtil.setTokenHeader(call, tokenResponse)
                call.respond(HttpStatusCode.OK, tokenResponse)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorRouting(
                        ErrorRoutingStatus.GENERIC_ERROR, e.message
                    )
                )
            }
        }

        post("/validate") {
            val accessTokenCookie = call.request.cookies["accessToken"]
            val refreshTokenCookie = call.request.cookies["refreshToken"]

            // Check if refresh token is present
            if (accessTokenCookie.isNullOrBlank() || refreshTokenCookie.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access or refresh token provided")
                )
                return@post
            }

            val (accessToken, refreshToken) = TokenResponse(accessTokenCookie, refreshTokenCookie)

            if (accessToken.isBlank() || refreshToken.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "No access or refresh token provided")
                )
                return@post
            }

            // Get user id from the token
            var userId = AuthRoutingUtil.decodeTokenToUid(
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                accessToken
            )

            // If access token has expired, use refresh token to create new tokens
            if (userId == null) {
                userId = AuthRoutingUtil.decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, refreshToken)

                // If refresh token has expired, return unauthorized
                if (userId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "Token expired")
                    )
                    return@post
                }

                // Check if user still is in the DB
                val userRow = userRepository.findUserById(userId)
                if (userRow == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User not found")
                    )
                    return@post
                }
                // If refresh token is valid, generate new tokens
                val tokenResponse = AuthRoutingUtil.generateTokens(
                    userRow[UsersTable.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(AuthRoutingUtil.accessTokenExpiryDate),
                    Date(AuthRoutingUtil.refreshTokenExpiryDate),
                )

                AuthRoutingUtil.setTokenHeader(call, tokenResponse)
                call.respond(HttpStatusCode.OK, tokenResponse)
                return@post
            }

            // Check if user still is in the DB
            val userRow = userRepository.findUserById(userId)
            if (userRow == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User not found")
                )
                return@post
            }

            // Generate new access token, leave refresh token
            val tokenResponse = AuthRoutingUtil.generateTokens(
                userRow[UsersTable.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                Date(AuthRoutingUtil.accessTokenExpiryDate),
                null
            )
            val tokens = TokenResponse(tokenResponse.accessToken, refreshToken)
            AuthRoutingUtil.setTokenHeader(call, tokens)
            call.respond(HttpStatusCode.OK, tokens)
        }
    }
}

