package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import com.trinimate.db.Users

class SessionService(private val chats: ChatService) {

    private fun response(row: ResultRow): SessionResponse {
        val joined = transaction {
            SessionParticipants.select { SessionParticipants.sessionId eq row[Sessions.id] }.count().toInt()
        }
        return SessionResponse(
            id = row[Sessions.id], sport = row[Sessions.sport], title = row[Sessions.title],
            dateTime = row[Sessions.dateTime], place = row[Sessions.place], fee = row[Sessions.fee],
            price = row[Sessions.price],
            playersNeeded = row[Sessions.playersNeeded], playersJoined = joined,
            createdBy = row[Sessions.createdBy],
            durationMinutes = row[Sessions.durationMinutes],
            level = row[Sessions.levelRequired],
            description = row[Sessions.description],
            tags = row[Sessions.tags],
            equipment = row[Sessions.equipment],
        )
    }
    fun leave(sessionId: Long, userId: Long) = transaction {
        val s = Sessions.select { Sessions.id eq sessionId }.single()
        require(s[Sessions.createdBy] != userId) { "Organizer cannot leave" }
        SessionParticipants.deleteWhere {
            (SessionParticipants.sessionId eq sessionId) and
                    (SessionParticipants.userId eq userId)
        }
        val name = Users.select { Users.id eq userId }.single()[Users.name]
        val remaining = SessionParticipants.select {
            SessionParticipants.sessionId eq sessionId
        }.count().toInt()
        upsertOrganizerStateNotification(
            organizer = s[Sessions.createdBy], sessionId = sessionId,
            title = "Player left",
            body = "$name left ${s[Sessions.title]} — now $remaining/${s[Sessions.playersNeeded]}",
        )
        chats.leaveSessionChat(sessionId, userId)
    }

    /** Rates another participant after the session happened. */
    fun rate(sessionId: Long, raterId: Long, req: com.trinimate.models.RateRequest) = transaction {
        require(req.stars in 1..5) { "Stars must be 1-5" }
        require(req.userId != raterId) { "You cannot rate yourself" }
        val s = Sessions.select { Sessions.id eq sessionId }.single()
        require(System.currentTimeMillis() > s[Sessions.dateTime]) { "Session hasn't happened yet" }
        val inSession = SessionParticipants.select {
            (SessionParticipants.sessionId eq sessionId) and
                    ((SessionParticipants.userId eq raterId) or (SessionParticipants.userId eq req.userId))
        }.count() == 2L
        require(inSession) { "Both players must be session participants" }
        Ratings.insertIgnore {
            it[Ratings.sessionId] = sessionId
            it[Ratings.raterId] = raterId
            it[Ratings.ratedId] = req.userId
            it[stars] = req.stars
            it[createdAt] = System.currentTimeMillis()
        }
        // recompute average rating for the rated user
        val avg = Ratings.select { Ratings.ratedId eq req.userId }
            .map { it[Ratings.stars].toDouble() }.average()
        Users.update({ Users.id eq req.userId }) { it[Users.rating] = avg }
        mapOf("rating" to avg)
    }
    fun list(): List<SessionResponse> = transaction {
        Sessions.select { Sessions.visibleToCommunity eq true }
            .orderBy(Sessions.dateTime, SortOrder.ASC)
            .map(::response)
    }

    fun get(id: Long): SessionResponse = transaction {
        response(Sessions.select { Sessions.id eq id }.single())
    }

    fun create(userId: Long, req: SessionRequest): SessionResponse = transaction {
        require(Sports.select { Sports.name eq req.sport }.count() > 0) { "Unknown sport" }
        val id = Sessions.insert {
            it[sport] = req.sport; it[title] = req.title; it[dateTime] = req.dateTime
            it[place] = req.place; it[fee] = req.fee; it[playersNeeded] = req.playersNeeded
            it[latitude] = req.latitude; it[longitude] = req.longitude
            it[visibleToCommunity] = req.visibleToCommunity
            it[createdBy] = userId; it[createdAt] = System.currentTimeMillis()
            it[price] = req.price
            it[durationMinutes] = req.durationMinutes
            it[levelRequired] = req.level
            it[description] = req.description
            it[tags] = req.tags?.joinToString(",")
            it[equipment] = req.equipment?.joinToString(",")
        } get Sessions.id
        SessionParticipants.insert {
            chats.joinSessionChat(id, userId)
            it[sessionId] = id; it[SessionParticipants.userId] = userId
            it[joinedAt] = System.currentTimeMillis()
        }
        response(Sessions.select { Sessions.id eq id }.single())
    }

    fun join(sessionId: Long, userId: Long): SessionResponse = transaction {
        val s = Sessions.select { Sessions.id eq sessionId }.single()
        val joined = SessionParticipants.select { SessionParticipants.sessionId eq sessionId }.count().toInt()
        require(joined < s[Sessions.playersNeeded]) { "Session is full" }

        val inserted = SessionParticipants.insertIgnore {
            it[SessionParticipants.sessionId] = sessionId
            it[SessionParticipants.userId] = userId
            it[joinedAt] = System.currentTimeMillis()
        }
        // duplicate-join guard → no duplicate notifications
        require(inserted.insertedCount > 0) { "You already joined this session" }

        val joinerName = Users.select { Users.id eq userId }.single()[Users.name]
        val spotsLeft = s[Sessions.playersNeeded] - joined - 1

        // ONE organizer notification — updated in place with the latest state
        upsertOrganizerStateNotification(
            organizer = s[Sessions.createdBy],
            sessionId = sessionId,
            title = "Player joined",
            body = "$joinerName joined ${s[Sessions.title]} — " +
                    if (spotsLeft > 0) "$spotsLeft spots left" else "session complete!",
        )
        // joiner confirmation
        Notifications.insert {
            it[Notifications.userId] = userId
            it[type] = "session"
            it[refId] = s[Sessions.createdBy]
            it[title] = "You're in!"
            it[body] = "You joined ${s[Sessions.title]} at ${s[Sessions.place]}"
            it[createdAt] = System.currentTimeMillis()
        }
        // group chat: creates the chat if needed, re-activates membership,
        // and posts "X joined the session" (handled inside)
        chats.joinSessionChat(sessionId, userId)

        response(Sessions.select { Sessions.id eq sessionId }.single())
    }

    fun update(id: Long, userId: Long, req: SessionRequest): SessionResponse = transaction {
        val s = Sessions.select { Sessions.id eq id }.single()
        require(Sports.select { Sports.name eq req.sport }.count() > 0) { "Unknown sport" }
        require(s[Sessions.createdBy] == userId) { "Only the organizer can edit" }
        Sessions.update({ Sessions.id eq id }) {
            it[title] = req.title
            it[dateTime] = req.dateTime
            it[place] = req.place
            it[fee] = req.fee
            it[price] = req.price
            it[playersNeeded] = req.playersNeeded
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[visibleToCommunity] = req.visibleToCommunity
            it[sport] = req.sport
            it[durationMinutes] = req.durationMinutes
            it[levelRequired] = req.level
            it[description] = req.description
            it[tags] = req.tags?.joinToString(",")
            it[equipment] = req.equipment?.joinToString(",")
        }
        response(Sessions.select { Sessions.id eq id }.single())
    }

    fun delete(id: Long, userId: Long) = transaction {
        val s = Sessions.select { Sessions.id eq id }.singleOrNull()
            ?: throw IllegalArgumentException("Session not found")
        require(s[Sessions.createdBy] == userId) { "Only the organizer can delete" }
        Sessions.deleteWhere { Sessions.id eq id } // participants cascade
    }

    fun participantIds(id: Long): List<Long> = transaction {
        SessionParticipants.select { SessionParticipants.sessionId eq id }
            .map { it[SessionParticipants.userId] }
    }

    fun kick(sessionId: Long, targetId: Long, requesterId: Long) = transaction {
        val s = Sessions.select { Sessions.id eq sessionId }.single()
        require(s[Sessions.createdBy] == requesterId) { "Only the organizer can remove players" }
        require(targetId != requesterId) { "Organizer cannot remove themselves" }
        SessionParticipants.deleteWhere {
            (SessionParticipants.sessionId eq sessionId) and
                    (SessionParticipants.userId eq targetId)
        }
        val name = Users.select { Users.id eq targetId }.single()[Users.name]
        val remaining = SessionParticipants.select {
            SessionParticipants.sessionId eq sessionId
        }.count().toInt()
        upsertOrganizerStateNotification(
            organizer = requesterId, sessionId = sessionId,
            title = "Player removed",
            body = "You removed $name from ${s[Sessions.title]} — now $remaining/${s[Sessions.playersNeeded]}",
        )
        chats.leaveSessionChat(sessionId, targetId)
    }
    /** ONE notification row per (organizer, session) — body overwritten with the latest state. */
    private fun upsertOrganizerStateNotification(
        organizer: Long, sessionId: Long, title: String, body: String,
    ) {
        val existing = Notifications.select {
            (Notifications.userId eq organizer) and
                    (Notifications.type eq "session") and
                    (Notifications.refId eq sessionId)
        }.singleOrNull()
        if (existing != null) {
            Notifications.update({ Notifications.id eq existing[Notifications.id] }) {
                it[Notifications.title] = title
                it[Notifications.body] = body
                it[Notifications.read] = false
                it[Notifications.createdAt] = System.currentTimeMillis()
            }
        } else {
            Notifications.insert {
                it[Notifications.userId] = organizer
                it[Notifications.type] = "session"
                it[Notifications.refId] = sessionId
                it[Notifications.title] = title
                it[Notifications.body] = body
                it[Notifications.createdAt] = System.currentTimeMillis()
            }
        }
    }
    /** Sessions the user joined or organized. role = "joined" | "organized" */
    fun mine(userId: Long, role: String): List<SessionResponse> = transaction {
        val rows = when (role) {
            "organized" -> Sessions.select { Sessions.createdBy eq userId }
            else -> (Sessions innerJoin SessionParticipants)
                .select { SessionParticipants.userId eq userId }
        }
        rows.orderBy(Sessions.dateTime, SortOrder.DESC).map(::response)
    }
}