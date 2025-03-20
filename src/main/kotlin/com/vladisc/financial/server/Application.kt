package com.vladisc.financial.server

import com.vladisc.financial.server.plugins.configureRouting
import com.vladisc.financial.server.plugins.healthCheck
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*

fun main() {
    embeddedServer(Netty, port = 7070) {
        install(CallLogging)
        healthCheckModule()
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureRouting()
}

fun Application.healthCheckModule() {
    healthCheck()
}