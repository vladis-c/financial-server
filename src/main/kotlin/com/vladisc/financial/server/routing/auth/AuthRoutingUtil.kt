package com.vladisc.financial.server.routing.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vladisc.financial.server.models.TokenResponse
import com.vladisc.financial.server.routing.RoutingUtil
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// TODO: Add `logout` endpoint: destroy JWT token
// TODO: Add `user/logs` endpoint: write all user actions: signup, login, added transaction and so on to a separate collection with datetime

object AuthRoutingUtil {
    private const val ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    private const val FOUR_WEEKS_MS = ONE_WEEK_MS * 4
    private const val USER_CLAIM = "uid"

    val accessTokenExpiryDate = System.currentTimeMillis() + ONE_WEEK_MS
    val refreshTokenExpiryDate = System.currentTimeMillis() + FOUR_WEEKS_MS


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
    ):  Int? {
       return RoutingUtil.decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, token, USER_CLAIM)
    }

    fun setTokenHeader(
        call: RoutingCall,
        tokens: TokenResponse,
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")

        // Create cookie for accessToken
        val accessTokenExp = dateFormat.format(accessTokenExpiryDate)
        val accessTokenCookie =
            "accessToken=${tokens.accessToken}; Expires=$accessTokenExp; HttpOnly; Max-Age=$ONE_WEEK_MS / 1000L}; Path=/; SameSite=Lax"
        call.response.header(HttpHeaders.SetCookie, accessTokenCookie)

        // Create cookie for refreshToken
        val refreshTokenExp = dateFormat.format(refreshTokenExpiryDate)
        val refreshTokenCookie =
            "refreshToken=${tokens.refreshToken}; Expires=$refreshTokenExp; HttpOnly; Max-Age=${FOUR_WEEKS_MS / 1000L}; Path=/; SameSite=Lax"
        call.response.header(HttpHeaders.SetCookie, refreshTokenCookie)
    }
}