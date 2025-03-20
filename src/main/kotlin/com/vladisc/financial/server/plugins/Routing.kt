package com.vladisc.financial.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.healthCheck() {
    routing {
        get("/health-check") {
            call.respondText("Server healthy")
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, my name is Vlad")
        }
        get("/users/{userId}") {
            val userId = call.parameters["userId"]
            val header = call.request.headers["Connection"]
            if (userId?.toIntOrNull() == 1) {
                call.response.header("CustomHeader", userId)
                call.response.status(HttpStatusCode(201, "Ok"))
            }
            call.respondText("Hello, $userId with $header")
        }
    }
}