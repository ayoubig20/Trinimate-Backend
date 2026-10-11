package com.trinimate.routes

import com.trinimate.models.*
import com.trinimate.security.userId
import com.trinimate.services.PlayerService
import com.trinimate.services.SessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.sessionRoutes() {
    val sessions by inject<SessionService>()
    val players by inject<PlayerService>()
    get("/sessions") { call.respond(sessions.list()) }
    get("/sessions/{id}") { call.respond(sessions.get(call.parameters["id"]!!.toLong())) }
    post("/sessions") { call.respond(sessions.create(call.userId, call.receive())) }
    post("/sessions/{id}/join") {
        try { call.respond(sessions.join(call.parameters["id"]!!.toLong(), call.userId)) }
        catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message!!)) }
    }
    post("/sessions/{id}/leave") {
        sessions.leave(call.parameters["id"]!!.toLong(), call.userId)
        call.respond(mapOf("message" to "Left session"))
    }
    post("/sessions/{id}/rate") {
        try {
            call.respond(sessions.rate(call.parameters["id"]!!.toLong(), call.userId, call.receive()))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message!!))
        }
    }
    put("/sessions/{id}") {
        try {
            call.respond(sessions.update(call.parameters["id"]!!.toLong(), call.userId, call.receive()))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(e.message!!))
        }
    }
    delete("/sessions/{id}") {
        try {
            sessions.delete(call.parameters["id"]!!.toLong(), call.userId)
            call.respond(mapOf("message" to "Session deleted"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(e.message!!))
        }
    }
    get("/sessions/{id}/participants") {
        val id = call.parameters["id"]!!.toLong()
        call.respond(sessions.participantIds(id).map { players.profile(it) })
    }

    post("/sessions/{id}/kick/{userId}") {
        try {
            sessions.kick(call.parameters["id"]!!.toLong(), call.parameters["userId"]!!.toLong(), call.userId)
            call.respond(mapOf("message" to "Player removed"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(e.message!!))
        }
    }
}