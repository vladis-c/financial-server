package com.vladisc.financial.server.routing.user

import com.vladisc.financial.server.routing.RoutingUtil
import kotlinx.serialization.Serializable

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