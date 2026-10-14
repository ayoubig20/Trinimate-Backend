package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.exceptions.ForbiddenException
import com.trinimate.exceptions.NotFoundException
import com.trinimate.models.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatService {
    private val connections = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
    private val json = Json { encodeDefaults = true }

    // ---------- ACCESS CONTROL ----------

    fun requireMember(chatId: Long, userId: Long) {
        val ok = transaction {
            val direct = Chats.select {
                (Chats.id eq chatId) and
                        ((Chats.userA eq userId) or (Chats.userB eq userId))
            }.count() > 0
            if (direct) return@transaction true
            ChatMembers.select {
                (ChatMembers.chatId eq chatId) and
                        (ChatMembers.userId eq userId) and
                        (ChatMembers.leftAt.isNull())
            }.count() > 0
        }
        if (!ok) throw ForbiddenException("Not your chat")
    }

    // ---------- CHATS ----------

    fun getOrCreateChat(me: Long, other: Long): Long {
        require(me != other) { "You cannot chat with yourself" }
        val low = minOf(me, other)
        val high = maxOf(me, other)

        fun find(): Long? = Chats.select { (Chats.userA eq low) and (Chats.userB eq high) }
            .singleOrNull()?.get(Chats.id)

        return transaction {
            require(Users.select { Users.id eq other }.count() > 0) { "User not found" }
            find() ?: try {
                Chats.insert {
                    it[userA] = low; it[userB] = high
                    it[createdAt] = System.currentTimeMillis()
                } get Chats.id
            } catch (e: ExposedSQLException) {
                find() ?: throw e
            }
        }
    }

    fun listChats(userId: Long): List<ChatListResponse> = transaction {
        // 1:1 chats (existing logic, unchanged)
        val chatRows: List<ResultRow> =
            Chats.select {
                ((Chats.userA eq userId) or (Chats.userB eq userId)) and
                        (Chats.sessionId.isNull())
            }.toList()
        // group chats where I'm an active member
        val myGroupIds = ChatMembers.select {
            (ChatMembers.userId eq userId) and (ChatMembers.leftAt.isNull())
        }.map { it[ChatMembers.chatId] }
        val groupRows: List<ResultRow> = if (myGroupIds.isEmpty()) emptyList()
        else (Chats innerJoin Sessions).select { Chats.id inList myGroupIds }.toList()

        fun lastOf(chatId: Long): ResultRow? =
            Messages.select { Messages.chatId eq chatId }
                .orderBy(Messages.sentAt to SortOrder.DESC).limit(1).firstOrNull()
        fun readUpTo(chatId: Long): Long = ChatReads.select {
            (ChatReads.userId eq userId) and (ChatReads.chatId eq chatId)
        }.firstOrNull()?.get(ChatReads.lastReadMessageId) ?: 0L
        fun lastText(last: ResultRow?): String = when (last?.get(Messages.kind)) {
            "session" -> "📅 Session confirmed"
            "location" -> "📍 Location shared"
            else -> last?.get(Messages.text) ?: ""
        }

        val direct: List<ChatListResponse> = if (chatRows.isEmpty()) emptyList()
        else {
            val peers: Map<Long, ResultRow> = Users.select {
                Users.id inList chatRows.map {
                    if (it[Chats.userA] == userId) it[Chats.userB]!! else it[Chats.userA]!!
                }.distinct()
            }.associateBy { it[Users.id] }
            chatRows.map { row ->
                val chatId = row[Chats.id]
                val peerId = if (row[Chats.userA] == userId) row[Chats.userB]!! else row[Chats.userA]!!
                val last = lastOf(chatId)
                val lastId = last?.get(Messages.id) ?: 0L
                ChatListResponse(
                    id = chatId, peerId = peerId,
                    peerName = peers[peerId]?.get(Users.name) ?: "Unknown",
                    peerAvatarUrl = peers[peerId]?.get(Users.avatarUrl),
                    lastMessage = lastText(last),
                    lastTime = last?.get(Messages.sentAt) ?: row[Chats.createdAt],
                    unread = last != null && last[Messages.senderId] != userId && lastId > readUpTo(chatId),
                )
            }
        }

        val group: List<ChatListResponse> = groupRows.map { row ->
            val chatId = row[Chats.id]
            val last = lastOf(chatId)
            val lastId = last?.get(Messages.id) ?: 0L
            ChatListResponse(
                id = chatId, peerId = 0,
                peerName = "🏟 ${row[Sessions.title]}",
                peerAvatarUrl = null,
                lastMessage = lastText(last),
                lastTime = last?.get(Messages.sentAt) ?: row[Chats.createdAt],
                unread = last != null && last[Messages.senderId] != userId && lastId > readUpTo(chatId),
                sessionId = row[Sessions.id],
            )
        }

        (direct + group).sortedByDescending { it.lastTime }
    }

    fun markRead(chatId: Long, userId: Long) {
        requireMember(chatId, userId)
        transaction {
            val lastId: Long? = Messages.select { Messages.chatId eq chatId }
                .orderBy(Messages.id to SortOrder.DESC).limit(1)
                .firstOrNull()?.get(Messages.id)
            if (lastId != null) {
                val updated = ChatReads.update({
                    (ChatReads.chatId eq chatId) and (ChatReads.userId eq userId)
                }) {
                    it[lastReadMessageId] = lastId
                }
                if (updated == 0) {
                    ChatReads.insert {
                        it[ChatReads.chatId] = chatId
                        it[ChatReads.userId] = userId
                        it[lastReadMessageId] = lastId
                    }
                }
            }
        }
    }

    // ---------- MESSAGES ----------

    fun messages(chatId: Long, me: Long): List<MessageResponse> {
        requireMember(chatId, me)
        return transaction {
            val rows: List<ResultRow> =
                Messages.select { Messages.chatId eq chatId }.orderBy(Messages.sentAt).toList()
            val ids: List<Long> = rows.map { it[Messages.id] }

            val reactionsByMessage: Map<Long, Map<String, Int>> =
                if (ids.isEmpty()) emptyMap()
                else Reactions.select { Reactions.messageId inList ids }
                    .groupBy { it[Reactions.messageId] }
                    .mapValues { (_, rs) -> rs.groupingBy { it[Reactions.emoji] }.eachCount() }

            rows.map {
                MessageResponse(
                    id = it[Messages.id], chatId = chatId, senderId = it[Messages.senderId],
                    text = it[Messages.text], sentAt = it[Messages.sentAt],
                    isMine = it[Messages.senderId] == me,
                    kind = it[Messages.kind],
                    reactions = reactionsByMessage[it[Messages.id]] ?: emptyMap(),
                )
            }
        }
    }

    fun save(chatId: Long, senderId: Long, text: String, kind: String = "text"): MessageResponse {
        requireMember(chatId, senderId)
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Message cannot be empty" }
        require(clean.length <= 2000) { "Message is too long" }
        val now = System.currentTimeMillis()

        val id: Long = transaction {
            Messages.insert {
                it[Messages.chatId] = chatId; it[Messages.senderId] = senderId
                it[Messages.text] = clean; it[sentAt] = now
                it[Messages.kind] = kind
            } get Messages.id
        }
        return MessageResponse(id, chatId, senderId, clean, now, isMine = false, kind = kind)
    }
    /**
     * Syncs session chats on every join/leave:
     * - one roster message per participant chat, updated in place (player list + count)
     * - a join/leave event message so other participants are informed
     * - on join: the joiner gets the "session" confirmation card + location
     * - on leave: the leaver gets a kick note and stops receiving updates
     */
    // ---------- SESSION GROUP CHAT ----------

    /** Open (or create) the group chat for a session; adds/re-activates membership. */
    fun joinSessionChat(sessionId: Long, userId: Long): Long = transaction {
        val session = Sessions.select { Sessions.id eq sessionId }.singleOrNull()
            ?: throw NotFoundException("Session not found")
        val organizer = session[Sessions.createdBy]

        val chatId = Chats.select { Chats.sessionId eq sessionId }
            .singleOrNull()?.get(Chats.id)
            ?: run {
                val newId = Chats.insert {
                    it[Chats.sessionId] = sessionId
                    it[userA] = organizer; it[userB] = organizer
                    it[createdAt] = System.currentTimeMillis()
                } get Chats.id
                // one-time welcome pack when the group is created
                insertRaw(newId, organizer,
                    "Group chat created for \"${session[Sessions.title]}\" 🏟", "system")
                insertRaw(newId, organizer, buildJsonObject {
                    put("title", session[Sessions.title])
                    put("place", session[Sessions.place])
                    put("dateTime", session[Sessions.dateTime])
                    put("players", session[Sessions.playersNeeded])
                }.toString(), "session")
                val lat = session[Sessions.latitude]
                val lng = session[Sessions.longitude]
                if (lat != null && lng != null) {
                    insertRaw(newId, organizer, "$lat,$lng", "location")
                }
                newId
            }

        // membership (rejoin clears leftAt)
        val existing = ChatMembers.select {
            (ChatMembers.chatId eq chatId) and (ChatMembers.userId eq userId)
        }.singleOrNull()
        if (existing != null) {
            if (existing[ChatMembers.leftAt] != null) {
                ChatMembers.update({
                    (ChatMembers.chatId eq chatId) and (ChatMembers.userId eq userId)
                }) { it[leftAt] = null }
            }
        } else {
            ChatMembers.insert {
                it[ChatMembers.chatId] = chatId
                it[ChatMembers.userId] = userId
                it[joinedAt] = System.currentTimeMillis()
            }
        }
        chatId
    }

    /** Marks the user as left and posts a system message. */
    fun leaveSessionChat(sessionId: Long, userId: Long) = transaction {
        val chatId = Chats.select { Chats.sessionId eq sessionId }
            .singleOrNull()?.get(Chats.id) ?: return@transaction
        val updated = ChatMembers.update({
            (ChatMembers.chatId eq chatId) and
                    (ChatMembers.userId eq userId) and
                    (ChatMembers.leftAt.isNull())
        }) { it[leftAt] = System.currentTimeMillis() }
        if (updated > 0) {
            val name = Users.select { Users.id eq userId }.single()[Users.name]
            insertRaw(chatId, userId, "$name left the session", "system")
        }
    }

    /** chatId for the session's group chat — only for current participants. */
    fun sessionChatFor(sessionId: Long, userId: Long): Long = transaction {
        val isParticipant = SessionParticipants.select {
            (SessionParticipants.sessionId eq sessionId) and
                    (SessionParticipants.userId eq userId)
        }.count() > 0
        if (!isParticipant) throw ForbiddenException("Join the session first")
        joinSessionChat(sessionId, userId)
    }

    private fun insertRaw(chatId: Long, senderId: Long, text: String, kind: String) {
        Messages.insert {
            it[Messages.chatId] = chatId
            it[Messages.senderId] = senderId
            it[Messages.text] = text
            it[Messages.kind] = kind
            it[Messages.sentAt] = System.currentTimeMillis()
        }
    }

    // ---------- REACTIONS ----------

    fun toggleReaction(messageId: Long, userId: Long, emoji: String) {
        require(emoji.isNotBlank() && emoji.length <= 8) { "Invalid emoji" }
        transaction {
            val chatId: Long = Messages.select { Messages.id eq messageId }
                .singleOrNull()?.get(Messages.chatId)
                ?: throw NotFoundException("Message not found")
            requireMember(chatId, userId)

            val deleted = Reactions.deleteWhere {
                (Reactions.messageId eq messageId) and
                        (Reactions.userId eq userId) and (Reactions.emoji eq emoji)
            }
            if (deleted == 0) {
                Reactions.insert {
                    it[Reactions.messageId] = messageId
                    it[Reactions.userId] = userId
                    it[Reactions.emoji] = emoji
                }
            }
        }
    }

    // ---------- WEBSOCKET ----------

    fun join(chatId: Long, userId: Long, session: WebSocketSession) {
        requireMember(chatId, userId)
        connections.computeIfAbsent(chatId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun leave(chatId: Long, session: WebSocketSession) {
        connections[chatId]?.let {
            it.remove(session)
            if (it.isEmpty()) connections.remove(chatId, it)
        }
    }

    suspend fun broadcast(chatId: Long, message: MessageResponse) {
        val payload = json.encodeToString(MessageResponse.serializer(), message)
        connections[chatId]?.forEach { s ->
            try { s.send(Frame.Text(payload)) }
            catch (_: Exception) { connections[chatId]?.remove(s) }
        }
    }
}