package com.trinimate.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.trinimate.models.*
import com.trinimate.security.JwtConfig
import com.trinimate.security.userId
import com.trinimate.services.ChatService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import com.trinimate.exceptions.ForbiddenException
fun Route.chatRoutes() {
    val chats by inject<ChatService>()

    // All conversations of the logged-in user (real data from ChatService)
    get("/chats") {
        call.respond(chats.listChats(call.userId))
    }
    post("/chats/{id}/read") {
        chats.markRead(call.parameters["id"]!!.toLong(), call.userId)
        call.respond(mapOf("ok" to true))
    }
    // Open (or create) a 1:1 chat with another user — returns the chatId
    post("/chats/with/{userId}") {
        val other = call.parameters["userId"]!!.toLong()
        val chatId = chats.getOrCreateChat(call.userId, other)
        call.respond(mapOf("chatId" to chatId))
    }

    get("/chats/{id}/messages") {
        call.respond(chats.messages(call.parameters["id"]!!.toLong(), call.userId))
    }

    post("/chats/{id}/messages") {
        val chatId = call.parameters["id"]!!.toLong()
        val req = call.receive<MessageRequest>()
        require(req.kind in setOf("text", "location")) { "Invalid message type" }
        val saved = chats.save(chatId, call.userId, req.text, req.kind)
        chats.broadcast(chatId, saved)
        call.respond(saved.copy(isMine = true))
    }
    post("/messages/{id}/reactions") {
        val emoji = (call.receive<Map<String, String>>())["emoji"] ?: "👍"
        chats.toggleReaction(call.parameters["id"]!!.toLong(), call.userId, emoji)
        call.respond(mapOf("ok" to true))
    }
    // WebSocket: ws://host:8080/ws/chat?chatId=1&token=<accessToken>
    webSocket("/ws/chat") {
        val token = call.request.queryParameters["token"] ?: return@webSocket close()
        val chatId = call.request.queryParameters["chatId"]?.toLongOrNull() ?: return@webSocket close()
        val decoded = try {
            JWT.require(Algorithm.HMAC256(JwtConfig.secret))
                .withAudience(JwtConfig.audience).withIssuer(JwtConfig.issuer)
                .build().verify(token)
        } catch (e: Exception) { return@webSocket close() }

        if (decoded.getClaim("type").asString() != "access") return@webSocket close()
        val userId = decoded.getClaim("userId").asLong()

        try { chats.join(chatId, userId, this) }
        catch (e: ForbiddenException) { return@webSocket close() }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    try {
                        chats.broadcast(chatId, chats.save(chatId, userId, frame.readText()))
                    } catch (_: IllegalArgumentException) { /* ignore empty/too long */ }
                }
            }
        } finally {
            chats.leave(chatId, this)
        }
    }
}