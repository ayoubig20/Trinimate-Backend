package com.trinimate.routes

import com.trinimate.security.userId
import com.trinimate.services.MiscService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import com.trinimate.models.*
fun Route.miscRoutes() {
    val misc by inject<MiscService>()
    get("/events") { call.respond(misc.events()) }
    get("/notifications") { call.respond(misc.notifications(call.userId)) }
    post("/events") { call.respond(misc.createEvent(call.userId, call.receive())) }
    post("/events/{id}/join") {
        misc.joinEvent(call.parameters["id"]!!.toLong(), call.userId)
        call.respond(mapOf("message" to "Joined event"))
    }
    post("/events/{id}/leave") {
        try {
            misc.leaveEvent(call.parameters["id"]!!.toLong(), call.userId)
            call.respond(mapOf("message" to "Left event"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message!!))
        }
    }
    delete("/events/{id}") {
        try {
            misc.deleteEvent(call.parameters["id"]!!.toLong(), call.userId)
            call.respond(mapOf("message" to "Event deleted"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(e.message!!))
        }
    }
}