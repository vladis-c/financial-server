package com.vladisc.financial.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vladisc.financial.server.models.ErrorRouting
import com.vladisc.financial.server.models.ErrorRoutingStatus
import com.vladisc.financial.server.models.Users
import com.vladisc.financial.server.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val oneWeekInMillis = 7 * 24 * 60 * 60 * 1000L
private const val fourWeeksInMillis = oneWeekInMillis * 4

private const val USER_CLAIM = "uid"

private val accessTokenExpiryDate = System.currentTimeMillis() + oneWeekInMillis
private val refreshTokenExpiryDate = System.currentTimeMillis() + fourWeeksInMillis

@Serializable
data class UserCredentials(val username: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String?)

fun Route.authRoutes(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/auth") {
        post("/signup") {
            try {
                val user = call.receive<UserCredentials>()

                // Add new user
                val success = userRepository.addUser(user.username, user.password)

                if (!success) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorRouting(
                            ErrorRoutingStatus.CONFLICT, "Username already exists"
                        )
                    )
                    return@post
                }

                // Get created user
                val userRow = userRepository.findUser(user.username)
                if (userRow === null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User ${user.username} not found")
                    )
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
                setTokenHeader(call, tokenResponse)
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

                // check for username in DB
                val userRow = userRepository.findUser(user.username)

                if (userRow == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRouting(ErrorRoutingStatus.NOT_FOUND, "User ${user.username} not found")
                    )
                    return@post
                }

                // Compare input pw and stored pw in DB
                if (!BCrypt.checkpw(user.password, userRow[Users.passwordHash])) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRouting(ErrorRoutingStatus.UNAUTHORIZED, "Invalid credentials")
                    )
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
                setTokenHeader(call, tokenResponse)
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
            var userId = decodeTokenToUid(
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                accessToken
            )

            // If access token has expired, use refresh token to create new tokens
            if (userId == null) {
                userId = decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, refreshToken)

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
                val tokenResponse = generateTokens(
                    userRow[Users.id],
                    jwtIssuer,
                    jwtAudience,
                    jwtSecret,
                    Date(accessTokenExpiryDate),
                    Date(refreshTokenExpiryDate),
                )

                setTokenHeader(call, tokenResponse)
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
            val tokenResponse = generateTokens(
                userRow[Users.id],
                jwtIssuer,
                jwtAudience,
                jwtSecret,
                Date(accessTokenExpiryDate),
                null
            )
            val tokens = TokenResponse(tokenResponse.accessToken, refreshToken)
            setTokenHeader(call, tokens)
            call.respond(HttpStatusCode.OK, tokens)

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
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    dateFormat.timeZone = TimeZone.getTimeZone("GMT")

    // Create cookie for accessToken
    val accessTokenCookie =
        "accessToken=${tokens.accessToken}; Expires=$dateFormat.format(accessTokenExpiryDate); HttpOnly; Max-Age=${oneWeekInMillis / 1000L}; Path=/; SameSite=Lax"
    call.response.header(HttpHeaders.SetCookie, accessTokenCookie)

    // Create cookie for refreshToken
    val refreshTokenCookie =
        "refreshToken=${tokens.refreshToken}; Expires=$dateFormat.format(refreshTokenExpiryDate); HttpOnly; Max-Age=${fourWeeksInMillis / 1000L}; Path=/; SameSite=Lax"
    call.response.header(HttpHeaders.SetCookie, refreshTokenCookie)

}

