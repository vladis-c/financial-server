package com.vladisc.financial.server

import com.vladisc.financial.server.plugins.configureRouting
import io.ktor.server.application.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    configureRouting()
}