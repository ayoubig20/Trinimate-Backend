package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class MiscService {

    // ---------- EVENTS ----------

    fun events(): List<EventResponse> = transaction {
        Events.selectAll().orderBy(Events.eventTime, SortOrder.ASC).map {
            val joined = EventParticipants
                .select { EventParticipants.eventId eq it[Events.id] }
                .count().toInt()
            EventResponse(
                id = it[Events.id],
                title = it[Events.title],
                place = it[Events.place],
                eventTime = it[Events.eventTime],
                sport = it[Events.sport],
                joinedCount = joined,
            )
        }
    }

    fun createEvent(userId: Long, req: EventRequest): EventResponse = transaction {
        val id = Events.insert {
            it[Events.title] = req.title
            it[Events.place] = req.place
            it[Events.eventTime] = req.eventTime
            it[Events.sport] = req.sport
            it[Events.latitude] = req.latitude
            it[Events.longitude] = req.longitude
            it[Events.createdBy] = userId
        } get Events.id
        EventParticipants.insert {
            it[EventParticipants.eventId] = id
            it[EventParticipants.userId] = userId
            it[EventParticipants.joinedAt] = System.currentTimeMillis()
        }
        EventResponse(
            id = id,
            title = req.title,
            place = req.place,
            eventTime = req.eventTime,
            sport = req.sport,
            joinedCount = 1,
        )
    }

    fun joinEvent(eventId: Long, userId: Long) = transaction {
        EventParticipants.insertIgnore {
            it[EventParticipants.eventId] = eventId
            it[EventParticipants.userId] = userId
            it[EventParticipants.joinedAt] = System.currentTimeMillis()
        }
    }

    fun leaveEvent(eventId: Long, userId: Long) = transaction {
        val e = Events.select { Events.id eq eventId }.single()
        require(e[Events.createdBy] != userId) { "Organizer cannot leave" }
        EventParticipants.deleteWhere {
            (EventParticipants.eventId eq eventId) and
                    (EventParticipants.userId eq userId)
        }
    }

    fun deleteEvent(eventId: Long, userId: Long) = transaction {
        val e = Events.select { Events.id eq eventId }.single()
        require(e[Events.createdBy] == userId) { "Only the organizer can delete" }
        Events.deleteWhere { Events.id eq eventId }
    }

    // ---------- NOTIFICATIONS ----------

    fun notifications(userId: Long): List<NotificationResponse> = transaction {
        Notifications.select { Notifications.userId eq userId }
            .orderBy(Notifications.createdAt, SortOrder.DESC).map {
                NotificationResponse(
                    id = it[Notifications.id],
                    type = it[Notifications.type],
                    title = it[Notifications.title],
                    body = it[Notifications.body],
                    read = it[Notifications.read],
                    createdAt = it[Notifications.createdAt],
                )
            }
    }

    fun notify(userId: Long, type: String, title: String, body: String) = transaction {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.type] = type
            it[Notifications.title] = title
            it[Notifications.body] = body
            it[Notifications.createdAt] = System.currentTimeMillis()
        }
    }
}