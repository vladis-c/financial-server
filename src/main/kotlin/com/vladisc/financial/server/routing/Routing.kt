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

        post("/login") {
            val credentials = call.receive<UserCredentials>()
            val user = UserCredentials(credentials.username, credentials.password)
            val userRow = userRepository.findUser(user.username)

            if (userRow == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            val storedPassword = userRow[Users.passwordHash] // Get stored password from DB

            if (!BCrypt.checkpw(user.password, storedPassword)) { // Compare passwords
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            }

            val expirationDate = Date(System.currentTimeMillis() + oneWeekInMillis)
            val token = JWT.create()
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .withClaim("userId", userRow[Users.id])
                .withExpiresAt(expirationDate)
                .sign(Algorithm.HMAC256(jwtSecret))

            call.response.cookies.append(
                "sessionid",
                token,
                expires = GMTDate(System.currentTimeMillis() + oneWeekInMillis),
                maxAge = oneWeekInMillis,
                httpOnly = true,
                secure = true,
                extensions = mapOf("SameSite" to "Lax")
            )

            call.response.header(HttpHeaders.Expires, expirationDate.time.toString())
            call.respond(HttpStatusCode.OK)
        }
    }
}


