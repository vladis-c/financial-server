package com.vladisc.financial.server.routing

import kotlinx.serialization.Serializable

object UserRoutingUtil {
    private const val USER_CLAIM = "uid"

    @Serializable
    data class User(val username: String, val id: Int)

    @Serializable
    data class PartialUser(
        val username: String? = null,
        val id: Int? = null,
        val newPassword: String? = null,
        val oldPassword: String? = null
    )

    fun decodeTokenToUid(
        jwtIssuer: String,
        jwtAudience: String,
        jwtSecret: String,
        token: String
    ): Int? {
        return RoutingUtil.decodeTokenToUid(jwtIssuer, jwtAudience, jwtSecret, token, USER_CLAIM)
    }

}