package com.vladisc.financial.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

object RoutingUtil {
    fun decodeTokenToUid(
        jwtIssuer: String,
        jwtAudience: String,
        jwtSecret: String,
        token: String,
        userClaim: String
    ): Int? {
        try {
            val decodedToken = JWT.require(Algorithm.HMAC256(jwtSecret))
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .build()
                .verify(token)
            return decodedToken.getClaim(userClaim).asInt()
        } catch (e: Exception) {
            return null
        }
    }
}