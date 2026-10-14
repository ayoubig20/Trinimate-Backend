package com.trinimate.routes

import com.trinimate.models.*
import com.trinimate.security.userId
import com.trinimate.services.ChatService
import com.trinimate.services.PlayerService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import io.ktor.http.content.*
import java.io.File
import java.util.UUID

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
private val avatarDir = File("uploads/avatars").apply { mkdirs() }

/** Decides the file type from the actual bytes, never from the client's filename. */
private fun imageExtension(b: ByteArray): String? = when {
    b.size > 12 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() &&
            b[2] == 0xFF.toByte() -> "jpg"
    b.size > 12 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> "png"
    b.size > 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
    else -> null
}

private fun deleteAvatarFile(url: String?) {
    if (url == null || !url.startsWith("/uploads/avatars/")) return
    File(avatarDir, File(url).name).delete()
}
fun Route.playerRoutes() {
    val players by inject<PlayerService>()
    val chats by inject<ChatService>()

    get("/players/nearby") {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
        val lng = call.request.queryParameters["lng"]?.toDoubleOrNull()
        val radius = call.request.queryParameters["radiusKm"]?.toDoubleOrNull() ?: 5.0
        val sport = call.request.queryParameters["sport"]
        if (lat == null || lng == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("lat and lng are required"))
            return@get
        }
        call.respond(players.nearby(lat, lng, radius, sport, call.userId))
    }

    get("/players/{id}") {
        call.respond(players.profile(call.parameters["id"]!!.toLong()))
    }

    post("/players/{id}/ping") {
        val to = call.parameters["id"]!!.toLong()
        val req = call.receiveNullable<PingRequest>() ?: PingRequest()
        call.respond(players.togglePing(call.userId, to, req.message))
    }
    post("/players/{id}/ping/accept") {
        val chatId = players.acceptPingFrom(call.parameters["id"]!!.toLong(), call.userId, chats)
        call.respond(mapOf("chatId" to chatId))
    }

    get("/profile") { call.respond(players.profile(call.userId)) }

    put("/profile") {
        call.respond(players.updateProfile(call.userId, call.receive()))
    }
    post("/profile/avatar") {
        var bytes: ByteArray? = null
        var tooLarge = false
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem && part.name == "file" && bytes == null) {
                val data = part.streamProvider().use { it.readNBytes(MAX_AVATAR_BYTES + 1) }
                if (data.size > MAX_AVATAR_BYTES) tooLarge = true else bytes = data
            }
            part.dispose()
        }
        if (tooLarge) {
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Image too large (max 5 MB)"))
            return@post
        }
        val data = bytes
        val ext = data?.let { imageExtension(it) }
        if (data == null || ext == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Please upload a JPG, PNG or WebP image"))
            return@post
        }
        val fileName = "${UUID.randomUUID()}.$ext"
        File(avatarDir, fileName).writeBytes(data)
        deleteAvatarFile(players.setAvatar(call.userId, "/uploads/avatars/$fileName"))
        call.respond(players.profile(call.userId))
    }

    delete("/profile/avatar") {
        deleteAvatarFile(players.setAvatar(call.userId, null))
        call.respond(players.profile(call.userId))
    }
}