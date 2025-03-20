package com.vladisc.financial.server

import com.vladisc.financial.server.plugins.configureRouting
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 7070) {
       module()
    }.start(wait = true)
}

fun Application.module() {
    configureRouting()
}