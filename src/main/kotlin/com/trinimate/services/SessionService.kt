package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class SessionService {

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
        )
    }
    fun leave(sessionId: Long, userId: Long) = transaction {
        val s = Sessions.select { Sessions.id eq sessionId }.single()
        require(s[Sessions.createdBy] != userId) { "Organizer cannot leave" }
        SessionParticipants.deleteWhere {
            (SessionParticipants.sessionId eq sessionId) and
                    (SessionParticipants.userId eq userId)
        }
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
        val id = Sessions.insert {
            it[sport] = req.sport; it[title] = req.title; it[dateTime] = req.dateTime
            it[place] = req.place; it[fee] = req.fee; it[playersNeeded] = req.playersNeeded
            it[latitude] = req.latitude; it[longitude] = req.longitude
            it[visibleToCommunity] = req.visibleToCommunity
            it[createdBy] = userId; it[createdAt] = System.currentTimeMillis()
        } get Sessions.id
        SessionParticipants.insert {
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

        // Notify the ORGANIZER
        Notifications.insert {
            it[Notifications.userId] = s[Sessions.createdBy]
            it[type] = "session"
            it[refId] = userId
            it[title] = "Player joined"
            it[body] = "$joinerName joined ${s[Sessions.title]} — $spotsLeft spots left"
            it[createdAt] = System.currentTimeMillis()
        }
        // Notify the JOINER (confirmation)
        Notifications.insert {
            it[Notifications.userId] = userId
            it[type] = "session"
            it[refId] = s[Sessions.createdBy]
            it[title] = "You're in!"
            it[body] = "You joined ${s[Sessions.title]} at ${s[Sessions.place]}"
            it[createdAt] = System.currentTimeMillis()
        }
        response(Sessions.select { Sessions.id eq sessionId }.single())
    }

    fun update(id: Long, userId: Long, req: SessionRequest): SessionResponse = transaction {
        val s = Sessions.select { Sessions.id eq id }.single()
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
    }
}