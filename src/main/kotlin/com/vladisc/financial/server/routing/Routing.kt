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

private const val USER_CLAIM = "uid"

private val accessTokenExpiryDate = System.currentTimeMillis() + oneWeekInMillis
private val refreshTokenExpiryDate = System.currentTimeMillis() + refreshTokenExpiry

@Serializable
data class UserCredentials(val username: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String?)

fun Route.authRoutes(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/auth") {
        post("/signup") {
            val user = call.receive<UserCredentials>()

            // Add new user
            val success = userRepository.addUser(user.username, user.password)

            if (!success) {
                call.respond(HttpStatusCode.Conflict, "Username already exists")
                return@post
            }

            // Get created user
            val userRow = userRepository.findUser(user.username)
            if (userRow === null) {
                call.respond(HttpStatusCode.NoContent, "Username is not found")
                return@post
            }

            // Generate tokens for new user user
            val tokenResponse = generateTokens(
                userRow[Users.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                Date(accessTokenExpiryDate),
                Date(refreshTokenExpiryDate),
                false
            )
            setTokenHeader(call, tokenResponse.accessToken, GMTDate(accessTokenExpiryDate))
            call.respond(HttpStatusCode.Created, tokenResponse)
        }

        post("/login") {
            val user = call.receive<UserCredentials>()

            // check for username in DB
            val userRow = userRepository.findUser(user.username)

            if (userRow == null) {
                call.respond(HttpStatusCode.NoContent, "Username is not found")
                return@post
            }

            // Compare input pw and stored pw in DB
            if (!BCrypt.checkpw(user.password, userRow[Users.passwordHash])) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            // Generate new access and refresh tokens
            val tokenResponse = generateTokens(
                userRow[Users.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                Date(accessTokenExpiryDate),
                Date(refreshTokenExpiryDate),
                false
            )
            setTokenHeader(call, tokenResponse.accessToken, GMTDate(accessTokenExpiryDate))
            call.respond(HttpStatusCode.OK, tokenResponse)
        }

        post("/validate") {
            val tokens = call.receive<TokenResponse>()

            try {
                val userId = decodeTokenToUid(
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    tokens.accessToken
                )

                val userRow = userRepository.findUserById(userId)

                if (userRow == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@post
                }

                val tokenResponse = generateTokens(
                    userRow[Users.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(accessTokenExpiryDate),
                    Date(refreshTokenExpiryDate),
                    true
                )
                setTokenHeader(call, tokenResponse.accessToken, GMTDate(accessTokenExpiryDate))
                call.respond(HttpStatusCode.OK, tokenResponse)
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
        .withClaim(USER_CLAIM, userId)
        .withExpiresAt(expirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))

    if (onlyAccessToken == true) {
        return TokenResponse(accessToken, null)
    }
    val refreshToken = JWT.create()
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .withClaim(USER_CLAIM, userId)
        .withExpiresAt(refreshExpirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))

    return TokenResponse(accessToken, refreshToken)
}

fun decodeTokenToUid(
    jwtIssuer: String,
    jwtAudience: String,
    jwtSecret: String,
    token: String
): Int {
    val decodedToken = JWT.require(Algorithm.HMAC256(jwtSecret))
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .build()
        .verify(token)
    return decodedToken.getClaim(USER_CLAIM).asInt()
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

