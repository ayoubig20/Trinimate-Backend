package com.trinimate.routes

import com.trinimate.models.*
import com.trinimate.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val auth by inject<AuthService>()

    post("/auth/register") {
        try { call.respond(auth.register(call.receive())) }
        catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message!!)) }
    }
    post("/auth/login") {
        try { call.respond(auth.login(call.receive())) }
        catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message!!)) }
    }
    post("/auth/refresh") {
        try { call.respond(auth.refresh(call.receive<RefreshRequest>().refreshToken)) }
        catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message!!)) }
    }
    post("/auth/forgot-password") {
        call.respond(auth.forgotPassword(call.receive<ForgotRequest>().email))
    }
}