package com.vladisc.financial.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vladisc.financial.server.models.Users
import com.vladisc.financial.server.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

private const val oneWeekInMillis = 7 * 24 * 60 * 60 * 1000L
private const val refreshTokenExpiry = oneWeekInMillis * 4

@Serializable
data class UserCredentials(val username: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String?)

fun Route.authRoutes(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/auth") {
        post("/signup") {
            val credentials = call.receive<UserCredentials>()
            val user = UserCredentials(credentials.username, credentials.password)
            val success = userRepository.addUser(user.username, user.password)
            if (success) {
                val userRow = userRepository.findUser(user.username)
                val tokenResponse = generateTokens(
                    userRow?.get(Users.id),
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(System.currentTimeMillis() + oneWeekInMillis),
                    Date(System.currentTimeMillis() + refreshTokenExpiry),
                    false
                )
                setTokenHeader(call, tokenResponse.accessToken, GMTDate(System.currentTimeMillis() + oneWeekInMillis))
                call.respond(HttpStatusCode.Created, tokenResponse)
            } else {
                call.respond(HttpStatusCode.Conflict, "Username already exists")
            }
        }

        post("/login") {
            val credentials = call.receive<UserCredentials>()
            val user = UserCredentials(credentials.username, credentials.password)
            val userRow = userRepository.findUser(user.username)

            if (userRow == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            val storedPassword = userRow[Users.passwordHash] // Get stored password from DB

            if (!BCrypt.checkpw(user.password, storedPassword)) { // Compare passwords
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            val expirationDate = Date(System.currentTimeMillis() + oneWeekInMillis)
            val tokenResponse = generateTokens(
                userRow[Users.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                expirationDate,
                Date(System.currentTimeMillis() + refreshTokenExpiry),
                false
            )
            setTokenHeader(call, tokenResponse.accessToken, GMTDate(System.currentTimeMillis() + oneWeekInMillis))
            call.respond(HttpStatusCode.OK, tokenResponse)
        }

        post("/validate") {
            val tokens = call.receive<TokenResponse>()

            try {
                val decodedToken = JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
                    .verify(tokens.accessToken)

                val expirationDate = Date(System.currentTimeMillis() + oneWeekInMillis)
                val userId = decodedToken.getClaim("userId").asInt()
                val userRow = userRepository.findUserById(userId)

                if (userRow != null) {
                    val tokenResponse = generateTokens(
                        userRow[Users.id],
                        jwtIssuer,
                        jwtAudience,
                        jwtSecret,
                        expirationDate,
                        Date(System.currentTimeMillis() + refreshTokenExpiry),
                        true
                    )
                    call.respond(HttpStatusCode.OK, mapOf("accessToken" to tokenResponse.accessToken))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid refresh token")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, "Token expired")
            }
        }

    }
}

fun generateTokens(
    userId: Int?,
    jwtIssuer: String,
    jwtAudience: String,
    jwtSecret: String,
    expirationDate: Date,
    refreshExpirationDate: Date,
    onlyAccessToken: Boolean?
): TokenResponse {
    val accessToken = JWT.create()
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .withClaim("userId", userId)
        .withExpiresAt(expirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))

    if (onlyAccessToken == true) {
        return TokenResponse(accessToken, null)
    }
    val refreshToken = JWT.create()
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .withClaim("userId", userId)
        .withExpiresAt(refreshExpirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))

    return TokenResponse(accessToken, refreshToken)
}

fun setTokenHeader(call: RoutingCall, token: String, expires: GMTDate) {
    call.response.cookies.append(
        "sessionid",
        token,
        expires = expires,
        maxAge = oneWeekInMillis,
        httpOnly = true,
        secure = true,
        extensions = mapOf("SameSite" to "Lax")
    )
}

