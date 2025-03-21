package com.vladisc.financial.server.routing

import com.vladisc.financial.server.models.Error
import com.vladisc.financial.server.models.ErrorStatus
import com.vladisc.financial.server.models.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.healthCheck() {
    routing {
        get("/health-check") {
            call.respondText("Server healthy")
        }
    }
}

fun Application.configureRouting() {
    routing {
        staticFiles("/resources", File("files")) {
            default("index.html")
            preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.name.toString())
            }
        }
        get("/") {
            call.respondText("Hello, my name is Vlad")
        }
        get("/users/{userId}") {
            val userId = call.parameters["userId"]
            val header = call.request.headers["Connection"]
            if (userId?.toIntOrNull() == 1) {
                call.response.header("CustomHeader", userId)
                call.response.status(HttpStatusCode(201, "OK"))
            }
            call.respondText("Hello, $userId with header $header")
        }
        get("/users") {
            try {
                val name = call.request.queryParameters["name"]
                if (name.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.Forbidden)
                    val error = Error(ErrorStatus.PARAMETER_MISSING, "No 'name' parameter")
                    call.respond(error)
                    return@get
                }
                val userId = call.request.queryParameters["id"]
                if (userId.isNullOrEmpty()) {
                    call.response.status(HttpStatusCode.Forbidden)
                    val error = Error(ErrorStatus.PARAMETER_MISSING, "No 'id' parameter")
                    call.respond(error)
                    return@get
                }
                val user = User(name, userId.toIntOrNull())
                call.respond(user)
            } catch (e: Exception) {
                val error = Error(ErrorStatus.GENERIC_ERROR, "${e.message}")
                call.respond(error)
            }

        }
    }
}

