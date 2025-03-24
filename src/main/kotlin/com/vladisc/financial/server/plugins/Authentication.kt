package com.vladisc.financial.server.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv

private const val USER_CLAIM = "uid"

fun Application.configureAuthentication() {
    val dotenv = dotenv()
    val jwtSecret = dotenv["JWT_SECRET"]
    val jwtUrl = dotenv["JWT_URL"]

    install(Authentication) {
        jwt {
            realm = "ktor application"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtUrl)
                    .withAudience(jwtUrl)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim(USER_CLAIM).asInt() != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}