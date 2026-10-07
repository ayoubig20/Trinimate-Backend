package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class SessionService {

    private fun response(row: ResultRow): SessionResponse {
        val joined = transaction {
            SessionParticipants.select { SessionParticipants.sessionId eq row[Sessions.id] }.count().toInt()
        }
        return SessionResponse(
            id = row[Sessions.id], sport = row[Sessions.sport], title = row[Sessions.title],
            dateTime = row[Sessions.dateTime], place = row[Sessions.place], fee = row[Sessions.fee],
            playersNeeded = row[Sessions.playersNeeded], playersJoined = joined,
            createdBy = row[Sessions.createdBy],
        )
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
        SessionParticipants.insertIgnore {
            it[SessionParticipants.sessionId] = sessionId; it[SessionParticipants.userId] = userId
            it[joinedAt] = System.currentTimeMillis()
        }
        response(s)
    }
}