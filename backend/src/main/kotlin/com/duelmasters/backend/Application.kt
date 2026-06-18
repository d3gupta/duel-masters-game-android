package com.duelmasters.backend

import com.duelmasters.backend.config.DatabaseFactory
import com.duelmasters.backend.config.ServerConfig
import com.duelmasters.backend.plugins.configureRouting
import com.duelmasters.backend.plugins.configureSerialization
import com.duelmasters.backend.plugins.configureSockets
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = ServerConfig.port, host = ServerConfig.host) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureSerialization()
    configureSockets()
    configureRouting()
}
