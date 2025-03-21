package com.vladisc.financial.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

fun Application.configureAuthentication() {
    val jwtSecret = "your_jwt_secret"
    val jwtIssuer = "http://0.0.0.0:7070/"
    val jwtAudience = "http://0.0.0.0:7070/"

    install(Authentication) {
        jwt {
            realm = "ktor application"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}