package com.trinimate.routes

import com.trinimate.models.*
import com.trinimate.security.userId
import com.trinimate.services.PlayerService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playerRoutes() {
    val players by inject<PlayerService>()

    get("/players/nearby") {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
        val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
        val radius = call.request.queryParameters["radiusKm"]?.toDoubleOrNull() ?: 5.0
        val sport = call.request.queryParameters["sport"]
        if (lat == null || lng == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("lat and lng are required"))
            return@get
        }
        call.respond(players.nearby(lat, lng, radius, sport))
    }

    get("/players/{id}") {
        call.respond(players.profile(call.parameters["id"]!!.toLong()))
    }

    post("/players/{id}/ping") {
        val to = call.parameters["id"]!!.toLong()
        val req = call.receiveNullable<PingRequest>() ?: PingRequest()
        players.ping(call.userId, to, req.message)
        call.respond(mapOf("message" to "Ping sent"))
    }

    post("/pings/{id}/accept") {
        players.acceptPing(call.parameters["id"]!!.toLong(), call.userId)
        call.respond(mapOf("message" to "Ping accepted"))
    }

    get("/profile") { call.respond(players.profile(call.userId)) }

    put("/profile") {
        call.respond(players.updateProfile(call.userId, call.receive()))
    }
}