package com.trinimate

import com.trinimate.di.appModule
import com.trinimate.plugins.*
import com.trinimate.routes.*
import com.trinimate.security.configureSecurity
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(Koin) { slf4jLogger(); modules(appModule) }
    configureSerialization()
    configureSecurity()
    configureSockets()
    configureHTTP()
    configureDatabases()
    routing {
        route("/api/v1") {
            authRoutes()
            authenticate("auth-jwt") {
                playerRoutes()
                sessionRoutes()
                chatRoutes()
                miscRoutes()
            }
        }
    }
}