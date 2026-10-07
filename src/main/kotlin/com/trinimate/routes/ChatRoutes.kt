package com.trinimate.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.trinimate.models.*
import com.trinimate.security.JwtConfig
import com.trinimate.security.userId
import com.trinimate.services.ChatService
import com.trinimate.services.PlayerService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.response.*
fun Route.chatRoutes() {
    val chats by inject<ChatService>()
    val players by inject<PlayerService>()

    get("/chats") { call.respond(emptyList<String>()) /* list user chats in Stage 6 refinement */ }

    get("/chats/{id}/messages") {
        call.respond(chats.messages(call.parameters["id"]!!.toLong(), call.userId))
    }

    post("/chats/{id}/messages") {
        val chatId = call.parameters["id"]!!.toLong()
        val saved = chats.save(chatId, call.userId, call.receive<MessageRequest>().text)
        chats.broadcast(chatId, saved.copy(isMine = true))
        call.respond(saved)
    }

    // WebSocket: ws://host:8080/ws/chat?chatId=1&token=<accessToken>
    webSocket("/ws/chat") {
        val token = call.request.queryParameters["token"] ?: run { close(); return@webSocket }
        val chatId = call.request.queryParameters["chatId"]?.toLongOrNull() ?: run { close(); return@webSocket }
        val decoded = try {
            JWT.require(Algorithm.HMAC256(JwtConfig.secret))
                .withAudience(JwtConfig.audience).withIssuer(JwtConfig.issuer).build().verify(token)
        } catch (e: Exception) { close(); return@webSocket }
        val userId = decoded.getClaim("userId").asLong()

        chats.join(chatId, this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val saved = chats.save(chatId, userId, frame.readText())
                    chats.broadcast(chatId, saved)
                }
            }
        } finally {
            chats.leave(chatId, this)
        }
    }
}