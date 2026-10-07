package com.trinimate.routes

import com.trinimate.services.MiscService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.miscRoutes() {
    val misc by inject<MiscService>()
    get("/events") { call.respond(misc.events()) }
    get("/notifications") { call.respond(misc.notifications(1L)) }
}