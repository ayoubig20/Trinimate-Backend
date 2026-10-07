package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

class ChatService {
    private val connections = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
    private val json = Json { encodeDefaults = true }

    fun getOrCreateChat(userA: Long, userB: Long): Long = transaction {
        Chats.select {
            ((Chats.userA eq userA) and (Chats.userB eq userB)) or
                    ((Chats.userA eq userB) and (Chats.userB eq userA))
        }.singleOrNull()?.get(Chats.id)
            ?: Chats.insert {
                it[Chats.userA] = userA; it[Chats.userB] = userB
                it[createdAt] = System.currentTimeMillis()
            } get Chats.id
    }

    fun messages(chatId: Long, me: Long): List<MessageResponse> = transaction {
        Messages.select { Messages.chatId eq chatId }.orderBy(Messages.sentAt).map {
            MessageResponse(
                id = it[Messages.id], chatId = chatId, senderId = it[Messages.senderId],
                text = it[Messages.text], sentAt = it[Messages.sentAt],
                isMine = it[Messages.senderId] == me,
            )
        }
    }

    fun save(chatId: Long, senderId: Long, text: String): MessageResponse = transaction {
        val id = Messages.insert {
            it[Messages.chatId] = chatId; it[Messages.senderId] = senderId
            it[Messages.text] = text; it[sentAt] = System.currentTimeMillis()
        } get Messages.id
        MessageResponse(id, chatId, senderId, text,
            System.currentTimeMillis(), isMine = false)
    }

    suspend fun join(chatId: Long, session: WebSocketSession) =
        connections.getOrPut(chatId) { mutableSetOf() }.add(session)

    fun leave(chatId: Long, session: WebSocketSession) =
        connections[chatId]?.remove(session)

    suspend fun broadcast(chatId: Long, message: MessageResponse) {
        val payload = json.encodeToString(MessageResponse.serializer(), message)
        connections[chatId]?.forEach { it.send(Frame.Text(payload)) }
    }
}