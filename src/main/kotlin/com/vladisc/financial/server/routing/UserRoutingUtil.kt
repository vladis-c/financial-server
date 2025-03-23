package com.vladisc.financial.server.routing

object UserRoutingUtil {
    private const val USER_CLAIM = "uid"
    fun decodeTokenToUid(
        jwtIssuer: String,
        jwtAudience: String,
        jwtSecret: String,
        token: String
    ): Int? {
        return RoutingUtil.decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, token, USER_CLAIM)
    }

}