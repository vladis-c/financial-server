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
            )
            setTokenHeader(call, tokenResponse, GMTDate(accessTokenExpiryDate), GMTDate(refreshTokenExpiryDate))
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
            )
            setTokenHeader(call, tokenResponse, GMTDate(accessTokenExpiryDate), GMTDate(refreshTokenExpiryDate))
            call.respond(HttpStatusCode.OK, tokenResponse)
        }

        post("/validate") {
            val tokens = call.receive<TokenResponse>()

            // Check if refresh token is present
            if (tokens.refreshToken.isNullOrBlank() || tokens.accessToken.isBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "No access or refresh token provided")
                return@post
            }

            var userId = decodeTokenToUid(
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                tokens.accessToken
            )

            // If access token has expired, use refresh token to create new tokens
            if (userId == null) {
                userId = decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, tokens.refreshToken)

                // If refresh token has expired, return unauthorized
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Token expired")
                    return@post
                }

                // Check if user still is in the DB
                val userRow = userRepository.findUserById(userId)
                if (userRow == null) {
                    call.respond(HttpStatusCode.Conflict, "User does not exist")
                    return@post
                }
                // If refresh token is valid, generate new tokens
                val tokenResponse = generateTokens(
                    userRow[Users.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(accessTokenExpiryDate),
                    Date(refreshTokenExpiryDate),
                )

                setTokenHeader(call, tokenResponse, GMTDate(accessTokenExpiryDate), GMTDate(refreshTokenExpiryDate))
                call.respond(HttpStatusCode.OK, tokenResponse)
                return@post
            }

            // Check if user still is in the DB
            val userRow = userRepository.findUserById(userId)
            if (userRow == null) {
                call.respond(HttpStatusCode.Conflict, "User does not exist")
                return@post
            }

            // Generate new access token, leave refresh token
            val tokenResponse = generateTokens(
                userRow[Users.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                Date(accessTokenExpiryDate),
                null
            )
            setTokenHeader(call, tokenResponse, GMTDate(accessTokenExpiryDate), GMTDate(refreshTokenExpiryDate))
            call.respond(HttpStatusCode.OK, TokenResponse(tokenResponse.accessToken, tokens.refreshToken))

        }

    }
}

fun generateTokens(
    userId: Int?,
    jwtIssuer: String,
    jwtAudience: String,
    jwtSecret: String,
    expirationDate: Date,
    refreshExpirationDate: Date?,
): TokenResponse {
    val accessToken = JWT.create()
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .withClaim(USER_CLAIM, userId)
        .withExpiresAt(expirationDate)
        .sign(Algorithm.HMAC256(jwtSecret))

    if (refreshExpirationDate == null) {
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
): Int? {
    try {
        val decodedToken = JWT.require(Algorithm.HMAC256(jwtSecret))
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .build()
            .verify(token)
        return decodedToken.getClaim(USER_CLAIM).asInt()
    } catch (e: Exception) {
        return null
    }

}

fun setTokenHeader(
    call: RoutingCall,
    tokens: TokenResponse,
    accessTokenExpires: GMTDate,
    refreshTokenExpires: GMTDate
) {
    call.response.header(HttpHeaders.SetCookie, "")
    call.response.cookies.append(
        "accessToken",
        tokens.accessToken,
        expires = accessTokenExpires,
        maxAge = oneWeekInMillis,
        httpOnly = true,
        secure = true,
        extensions = mapOf("SameSite" to "Lax")
    )
    call.response.cookies.append(
        "refreshToken",
        tokens.refreshToken ?: "",
        expires = refreshTokenExpires,
        maxAge = oneWeekInMillis,
        httpOnly = true,
        secure = true,
        extensions = mapOf("SameSite" to "Lax")
    )
}

