package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    private fun reactionsFor(messageId: Long): Map<String, Int> = transaction {
        Reactions.select { Reactions.messageId eq messageId }
            .groupBy({ it[Reactions.emoji] }, { it[Reactions.userId] })
            .mapValues { it.value.size }
    }

    fun messages(chatId: Long, me: Long): List<MessageResponse> = transaction {
        Messages.select { Messages.chatId eq chatId }.orderBy(Messages.sentAt).map {
            MessageResponse(
                id = it[Messages.id], chatId = chatId, senderId = it[Messages.senderId],
                text = it[Messages.text], sentAt = it[Messages.sentAt],
                isMine = it[Messages.senderId] == me,
                kind = it[Messages.kind],
                reactions = reactionsFor(it[Messages.id]),
            )
        }
    }

    fun save(chatId: Long, senderId: Long, text: String, kind: String = "text"): MessageResponse = transaction {
        val id = Messages.insert {
            it[Messages.chatId] = chatId; it[Messages.senderId] = senderId
            it[Messages.text] = text; it[sentAt] = System.currentTimeMillis()
            it[Messages.kind] = kind
        } get Messages.id
        MessageResponse(id, chatId, senderId, text,
            System.currentTimeMillis(), isMine = false, kind = kind)
    }

    /** Organizer-side confirmation sent automatically when someone joins. */
    fun notifyJoin(session: ResultRow, joinerId: Long) {
        val organizer: Long = session[Sessions.createdBy]
        val chatId: Long = getOrCreateChat(joinerId, organizer)
        val dt = java.time.Instant.ofEpochMilli(session[Sessions.dateTime])
            .atZone(java.time.ZoneId.systemDefault())
        save(
            chatId,
            organizer,
            "Session confirmed: \"${session[Sessions.title]}\" at ${session[Sessions.place]}, " +
                    "${dt.dayOfMonth}/${dt.monthValue} ${dt.hour}:${dt.minute.toString().padStart(2, '0')} — " +
                    "${session[Sessions.playersNeeded]} players needed",
            "system",
        )
        val lat: Double? = session[Sessions.latitude]
        val lng: Double? = session[Sessions.longitude]
        if (lat != null && lng != null) {
            save(chatId, organizer, "$lat,$lng", "location")
        }
    }

    fun toggleReaction(messageId: Long, userId: Long, emoji: String) = transaction {
        val exists = Reactions.select {
            (Reactions.messageId eq messageId) and
                    (Reactions.userId eq userId) and (Reactions.emoji eq emoji)
        }.count() > 0
        if (exists) {
            Reactions.deleteWhere {
                (Reactions.messageId eq messageId) and
                        (Reactions.userId eq userId) and (Reactions.emoji eq emoji)
            }
        } else {
            Reactions.insert {
                it[Reactions.messageId] = messageId
                it[Reactions.userId] = userId
                it[Reactions.emoji] = emoji
            }
        }
    }

    fun listChats(userId: Long): List<ChatListResponse> = transaction {
        Chats.select { (Chats.userA eq userId) or (Chats.userB eq userId) }.map { row ->
            val chatId = row[Chats.id]
            val peerId = if (row[Chats.userA] == userId) row[Chats.userB] else row[Chats.userA]
            val peerName = Users.select { Users.id eq peerId }.single()[Users.name]
            val last = Messages.select { Messages.chatId eq chatId }
                .orderBy(Messages.sentAt to SortOrder.DESC).limit(1).firstOrNull()
            ChatListResponse(
                chatId, peerId, peerName,
                last?.get(Messages.text) ?: "",
                last?.get(Messages.sentAt) ?: row[Chats.createdAt],
            )
        }.sortedByDescending { it.lastTime }
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