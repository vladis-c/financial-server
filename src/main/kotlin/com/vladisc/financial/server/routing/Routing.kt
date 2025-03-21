package com.vladisc.financial.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vladisc.financial.server.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import kotlinx.serialization.Serializable

@Serializable
data class UserCredentials(val username: String, val password: String)

fun Route.authRoutes(userRepository: UserRepository, jwtIssuer: String, jwtAudience: String, jwtSecret: String) {
    route("/auth") {
        post("/signup") {
            val credentials = call.receive<UserCredentials>()
            val user = UserCredentials(credentials.username, credentials.password)
            val success = userRepository.addUser(user.username, user.password)
            if (success) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.Conflict, "Username already exists")
            }
        }

//        post("/login") {
//            val credentials = call.receive<UserCredentials>()
//            val user = userRepository.findUser(credentials.username)
//
//            if (user != null && BCrypt.checkpw(credentials.password, user.second)) {
//                val token = JWT.create()
//                    .withIssuer(jwtIssuer)
//                    .withAudience(jwtAudience)
//                    .withClaim("userId", user.first)
//                    .sign(Algorithm.HMAC256(jwtSecret))
//
//                call.respond(hashMapOf("token" to token))
//            } else {
//                call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
//            }
//        }
    }
}
