package com.trinimate.routes

import com.trinimate.models.*
import com.trinimate.security.userId
import com.trinimate.services.SessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.sessionRoutes() {
    val sessions by inject<SessionService>()

    get("/sessions") { call.respond(sessions.list()) }
    get("/sessions/{id}") { call.respond(sessions.get(call.parameters["id"]!!.toLong())) }
    post("/sessions") { call.respond(sessions.create(call.userId, call.receive())) }
    post("/sessions/{id}/join") {
        try { call.respond(sessions.join(call.parameters["id"]!!.toLong(), call.userId)) }
        catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message!!)) }
    }
}